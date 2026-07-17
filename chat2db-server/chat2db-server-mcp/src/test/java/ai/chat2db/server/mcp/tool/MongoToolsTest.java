package ai.chat2db.server.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import ai.chat2db.server.domain.api.model.DataSource;
import ai.chat2db.server.domain.api.service.DlTemplateService;
import ai.chat2db.server.mcp.config.McpProperties.Mongo;
import ai.chat2db.server.mcp.config.McpProperties.Sql;
import ai.chat2db.server.mcp.config.McpServerConfigService;
import ai.chat2db.server.mcp.support.ConnectionScope;
import ai.chat2db.server.mcp.support.MongoToolExecutionSupport;
import ai.chat2db.server.mcp.support.MqlGuard;
import ai.chat2db.server.mcp.support.PreparedMongoChangeStore;
import ai.chat2db.server.mcp.support.ResolvedDataSource;
import ai.chat2db.server.mcp.support.ResultFormatter;
import ai.chat2db.server.mcp.support.SqlGuard;
import ai.chat2db.spi.model.ExecuteResult;
import ai.chat2db.spi.model.Header;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MongoToolsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private DlTemplateService dlTemplateService;
    private MongoTools tools;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ConnectionScope connectionScope = mock(ConnectionScope.class);
        ResolvedDataSource resolvedDataSource = ResolvedDataSource.builder()
            .dataSourceId(1L)
            .dataSource(mongodbDataSource())
            .summary("id=1;name=mongo;env=dev")
            .build();
        when(connectionScope.run(anyString(), anyString(), isNull(), any(Function.class)))
            .thenAnswer(invocation -> ((Function<ResolvedDataSource, Object>) invocation.getArgument(3))
                .apply(resolvedDataSource));

        McpServerConfigService configService = mock(McpServerConfigService.class);
        Mongo config = new Mongo();
        config.setMaxBatchDocuments(100);
        when(configService.getEffectiveMongo()).thenReturn(config);
        Sql sharedPolicy = new Sql();
        sharedPolicy.setAllowWrite(true);
        sharedPolicy.setAllowDdl(true);
        when(configService.getEffectiveSql()).thenReturn(sharedPolicy);
        SqlGuard sqlGuard = mock(SqlGuard.class);
        when(sqlGuard.isReleaseDatasource(1L)).thenReturn(false);
        when(sqlGuard.normalizePageSize(any())).thenAnswer(invocation -> {
            Integer value = invocation.getArgument(0);
            return value == null ? 100 : value;
        });
        MqlGuard mqlGuard = new MqlGuard(configService, sqlGuard);

        dlTemplateService = mock(DlTemplateService.class);
        MongoToolExecutionSupport executionSupport = new MongoToolExecutionSupport(dlTemplateService, objectMapper);
        tools = new MongoTools(connectionScope, executionSupport, mqlGuard, new PreparedMongoChangeStore(),
            new ResultFormatter(objectMapper));
    }

    @Test
    void rejectsEmptyWriteFilterAndOversizedInsertMany() {
        assertThatThrownBy(() -> tools.prepareMongoWriteChange("chat2db://datasources/1", "app",
            "UPDATE_ONE", "orders", Map.of(), null, null, Map.of("$set", Map.of("x", 1)), null))
            .hasMessageContaining("non-empty filter");

        List<Map<String, Object>> documents = java.util.stream.IntStream.range(0, 101)
            .mapToObj(index -> Map.<String, Object>of("index", index))
            .toList();
        assertThatThrownBy(() -> tools.prepareMongoWriteChange("chat2db://datasources/1", "app",
            "INSERT_MANY", "orders", null, null, documents, null, null))
            .hasMessageContaining("max-batch-documents 100");

        assertThatThrownBy(() -> tools.prepareMongoWriteChange("chat2db://datasources/1", "app",
            "INSERT_ONE", "orders", Map.of("ignored", true), Map.of("x", 1), null, null, null))
            .hasMessageContaining("filter is not supported");
    }

    @Test
    void validatesAdminParameterMatrix() {
        assertThatThrownBy(() -> tools.prepareMongoAdminChange("chat2db://datasources/1", "app",
            "DROP_DATABASE", "orders", null, null))
            .hasMessageContaining("collection is not supported");
        assertThatThrownBy(() -> tools.prepareMongoAdminChange("chat2db://datasources/1", "app",
            "CREATE_INDEX", "orders", "unexpected", Map.of("createdAt", -1)))
            .hasMessageContaining("indexName is not supported");
    }

    @Test
    void preparesCanonicalStructuredWrite() throws Exception {
        when(dlTemplateService.execute(any())).thenReturn(countResult(2));

        String response = tools.prepareMongoWriteChange("chat2db://datasources/1", "app", "UPDATE_MANY",
            "orders", Map.of("status", "OPEN"), null, null, Map.of("$set", Map.of("status", "DONE")), 5);
        JsonNode json = objectMapper.readTree(response);

        assertThat(json.get("operationType").asText()).isEqualTo("UPDATE_MANY");
        assertThat(json.get("estimatedMatchedDocuments").asLong()).isEqualTo(2);
        assertThat(json.get("canonicalPreview").asText())
            .isEqualTo("db.orders.updateMany({\"status\":\"OPEN\"},{\"$set\":{\"status\":\"DONE\"}})");
        assertThat(json.get("confirmationText").asText()).contains("EXECUTE MONGODB UPDATE_MANY");
    }

    @Test
    void rejectsPrepareAndExecuteWhenMultiWriteExceedsMaximum() throws Exception {
        when(dlTemplateService.execute(any())).thenReturn(countResult(3));
        assertThatThrownBy(() -> tools.prepareMongoWriteChange("chat2db://datasources/1", "app", "DELETE_MANY",
            "orders", Map.of("expired", true), null, null, null, 2))
            .hasMessageContaining("exceed maxAffectedDocuments 2");

        when(dlTemplateService.execute(any())).thenReturn(countResult(2), countResult(4));
        String prepared = tools.prepareMongoWriteChange("chat2db://datasources/1", "app", "DELETE_MANY",
            "orders", Map.of("expired", true), null, null, null, 3);
        JsonNode json = objectMapper.readTree(prepared);
        assertThatThrownBy(() -> tools.executePreparedMongoChange(json.get("operationId").asText(),
            json.get("confirmationText").asText()))
            .hasMessageContaining("now exceed maxAffectedDocuments 3");
    }

    @Test
    void mcpSchemaExposesExactlyFourMongoTools() {
        List<io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification> specifications =
            org.springframework.ai.mcp.annotation.spring.SyncMcpAnnotationProviders.toolSpecifications(List.of(tools));

        assertThat(specifications).extracting(specification -> specification.tool().name())
            .containsExactlyInAnyOrder("executeMongoQuery", "prepareMongoWriteChange",
                "prepareMongoAdminChange", "executePreparedMongoChange");
    }

    private List<ExecuteResult> countResult(long count) {
        ExecuteResult result = ExecuteResult.builder()
            .success(true)
            .headerList(List.of(Header.builder().name("count").build()))
            .dataList(List.of(List.of(Long.toString(count))))
            .build();
        return List.of(result);
    }

    private DataSource mongodbDataSource() {
        DataSource dataSource = new DataSource();
        dataSource.setId(1L);
        dataSource.setAlias("mongo");
        dataSource.setType("MONGODB");
        return dataSource;
    }
}
