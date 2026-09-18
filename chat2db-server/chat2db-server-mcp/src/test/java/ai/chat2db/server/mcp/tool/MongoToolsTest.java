package ai.chat2db.server.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import ai.chat2db.server.domain.api.model.DataSource;
import ai.chat2db.server.domain.api.param.DlExecuteParam;
import ai.chat2db.server.domain.api.service.DataSourceService;
import ai.chat2db.server.domain.api.service.DatabaseService;
import ai.chat2db.server.domain.api.service.DlTemplateService;
import ai.chat2db.server.mcp.config.McpProperties.Mongo;
import ai.chat2db.server.mcp.config.McpProperties.Sql;
import ai.chat2db.server.mcp.config.McpServerConfigService;
import ai.chat2db.server.mcp.support.ConnectionScope;
import ai.chat2db.server.mcp.support.McpApprovalSupport;
import ai.chat2db.server.mcp.support.McpTestSupport;
import ai.chat2db.server.mcp.support.MongoToolExecutionSupport;
import ai.chat2db.server.mcp.support.MqlGuard;
import ai.chat2db.server.mcp.support.ResolvedDataSource;
import ai.chat2db.server.mcp.support.ResultFormatter;
import ai.chat2db.server.mcp.support.SqlGuard;
import ai.chat2db.spi.model.ExecuteResult;
import ai.chat2db.spi.model.Header;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.mcp.annotation.spring.SyncMcpAnnotationProviders;

class MongoToolsTest {

    private static final String DATASOURCE_URI = "chat2db://datasources/1";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private DlTemplateService dlTemplateService;
    private DatabaseService databaseService;
    private DataSourceService dataSourceService;
    private McpServerConfigService configService;
    private DataSource dataSource;
    private Mongo mongoConfig;
    private Sql sharedPolicy;
    private McpSyncServerExchange exchange;
    private MongoTools tools;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        McpTestSupport.initializeMessages();
        ConnectionScope connectionScope = mock(ConnectionScope.class);
        dataSource = new DataSource();
        dataSource.setId(1L);
        dataSource.setAlias("mongo");
        dataSource.setType("MONGODB");
        ResolvedDataSource resolvedDataSource = ResolvedDataSource.builder()
            .dataSourceId(1L).dataSource(dataSource).summary("id=1;name=mongo;env=dev").build();
        when(connectionScope.run(anyString(), anyString(), isNull(), any(Function.class)))
            .thenAnswer(invocation -> ((Function<ResolvedDataSource, Object>) invocation.getArgument(3))
                .apply(resolvedDataSource));

        configService = mock(McpServerConfigService.class);
        mongoConfig = new Mongo();
        mongoConfig.setMaxBatchDocuments(100);
        when(configService.getEffectiveMongo()).thenReturn(mongoConfig);
        sharedPolicy = new Sql();
        sharedPolicy.setAllowWrite(true);
        sharedPolicy.setAllowDdl(true);
        when(configService.getEffectiveSql()).thenReturn(sharedPolicy);
        when(configService.getApprovalClientKeywords()).thenReturn(List.of());
        dataSourceService = mock(DataSourceService.class);
        when(dataSourceService.queryExistent(eq(1L), any())).thenReturn(dataSource);
        SqlGuard sqlGuard = new SqlGuard(configService, dataSourceService);
        MqlGuard mqlGuard = new MqlGuard(configService, sqlGuard);

        dlTemplateService = mock(DlTemplateService.class);
        when(dlTemplateService.execute(any())).thenReturn(successResult());
        databaseService = mock(DatabaseService.class);
        MongoToolExecutionSupport executionSupport = new MongoToolExecutionSupport(dlTemplateService, objectMapper);
        tools = new MongoTools(connectionScope, databaseService, executionSupport, mqlGuard,
            new McpApprovalSupport(configService), new ResultFormatter(objectMapper));

        exchange = mock(McpSyncServerExchange.class);
        when(exchange.getClientInfo()).thenReturn(new McpSchema.Implementation("test-client", "Test client", "1.0"));
        when(exchange.getClientCapabilities()).thenReturn(new McpSchema.ClientCapabilities(
            null, null, null, new McpSchema.ClientCapabilities.Elicitation()));
        when(exchange.createElicitation(any(McpSchema.ElicitRequest.class))).thenReturn(accepted());
    }

    @Test
    void rejectsInvalidStructuredWritesBeforeApproval() {
        assertThatThrownBy(() -> tools.executeMongoWrite(exchange, DATASOURCE_URI, "app",
            "UPDATE_ONE", "orders", Map.of(), null, null, Map.of("$set", Map.of("x", 1)), null))
            .hasMessageContaining("non-empty filter");
        List<Map<String, Object>> documents = java.util.stream.IntStream.range(0, 101)
            .mapToObj(index -> Map.<String, Object>of("index", index)).toList();
        assertThatThrownBy(() -> tools.executeMongoWrite(exchange, DATASOURCE_URI, "app",
            "INSERT_MANY", "orders", null, null, documents, null, null))
            .hasMessageContaining("max-batch-documents 100");
        assertThatThrownBy(() -> tools.executeMongoWrite(exchange, DATASOURCE_URI, "app",
            "INSERT_ONE", "orders", Map.of("ignored", true), Map.of("x", 1), null, null, null))
            .hasMessageContaining("filter is not supported");
        assertThatThrownBy(() -> tools.executeMongoWrite(exchange, DATASOURCE_URI, "app",
            "UPDATE_ONE", "orders", Map.of("id", 1), null, null, Map.of("x", 1), null))
            .hasMessageContaining("update operators");
        verify(exchange, never()).createElicitation(any());
        verify(dlTemplateService, never()).execute(any());
    }

    @Test
    void validatesAdminParameterMatrixBeforeApproval() {
        assertThatThrownBy(() -> tools.executeMongoAdmin(exchange, DATASOURCE_URI, "app",
            "DROP_DATABASE", "orders", null, null)).hasMessageContaining("collection is not supported");
        assertThatThrownBy(() -> tools.executeMongoAdmin(exchange, DATASOURCE_URI, "app",
            "CREATE_INDEX", "orders", "unexpected", Map.of("createdAt", -1)))
            .hasMessageContaining("indexName is not supported");
        verify(exchange, never()).createElicitation(any());
        verify(dlTemplateService, never()).execute(any());
    }

    @Test
    void acceptedWriteExecutesExactReviewedMqlDespiteNestedInputMutation() {
        Map<String, Object> filter = new LinkedHashMap<>(Map.of("status", "OPEN"));
        List<String> labels = new ArrayList<>(List.of("reviewed"));
        String note = "exact reviewed payload ".repeat(30);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("status", "DONE");
        fields.put("labels", labels);
        fields.put("note", note);
        Map<String, Object> update = new LinkedHashMap<>(Map.of("$set", fields));
        String expectedMql = "db.orders.updateMany({\"status\":\"OPEN\"},{\"$set\":{\"status\":\"DONE\","
            + "\"labels\":[\"reviewed\"],\"note\":\"" + note + "\"}})";
        when(dlTemplateService.execute(any())).thenReturn(countResult(2), countResult(2), successResult());
        when(exchange.createElicitation(any())).thenAnswer(invocation -> {
            assertThat(((McpSchema.ElicitRequest) invocation.getArgument(0)).message()).contains(expectedMql);
            filter.clear();
            fields.put("status", "CHANGED");
            labels.add("not approved");
            update.clear();
            return accepted();
        });

        assertThat(tools.executeMongoWrite(exchange, DATASOURCE_URI, "app", "UPDATE_MANY",
            "orders", filter, null, null, update, 5)).contains("success: true");

        ArgumentCaptor<McpSchema.ElicitRequest> approval = ArgumentCaptor.forClass(McpSchema.ElicitRequest.class);
        verify(exchange).createElicitation(approval.capture());
        assertThat(approval.getValue().message()).contains("id=1;name=mongo;env=dev/app.orders")
            .contains("\"estimatedMatchedDocuments\":2", "\"maxAffectedDocuments\":5");
        ArgumentCaptor<DlExecuteParam> executions = ArgumentCaptor.forClass(DlExecuteParam.class);
        verify(dlTemplateService, times(3)).execute(executions.capture());
        assertThat(executions.getAllValues()).extracting(DlExecuteParam::getSql).containsExactly(
            "db.orders.countDocuments({\"status\":\"OPEN\"})",
            "db.orders.countDocuments({\"status\":\"OPEN\"})", expectedMql);
        verify(dataSourceService, times(2)).queryExistent(eq(1L), any());
    }

    @Test
    void acceptedAdminPreservesReviewedIndexOrderAndValues() {
        Map<String, Object> keys = new LinkedHashMap<>();
        keys.put("status", 1);
        keys.put("createdAt", -1);
        String mql = "db.getCollection(\"order-items\").createIndex({\"status\":1,\"createdAt\":-1})";
        when(exchange.createElicitation(any())).thenAnswer(invocation -> {
            assertThat(((McpSchema.ElicitRequest) invocation.getArgument(0)).message()).contains(mql);
            keys.clear();
            keys.put("unapproved", 1);
            return accepted();
        });

        assertThat(tools.executeMongoAdmin(exchange, DATASOURCE_URI, "app", "CREATE_INDEX", "order-items", null,
            keys)).contains("success: true");

        ArgumentCaptor<DlExecuteParam> execution = ArgumentCaptor.forClass(DlExecuteParam.class);
        verify(dlTemplateService).execute(execution.capture());
        assertThat(execution.getValue().getSql()).isEqualTo(mql);
        verify(dataSourceService, times(2)).queryExistent(eq(1L), any());
    }

    @Test
    void insertManySnapshotsNestedDocumentsAndTheBatchItself() {
        Map<String, Object> nested = new LinkedHashMap<>(Map.of("value", "reviewed"));
        Map<String, Object> document = new LinkedHashMap<>(Map.of("nested", nested));
        List<Map<String, Object>> documents = new ArrayList<>(List.of(document));
        when(exchange.createElicitation(any())).thenAnswer(invocation -> {
            nested.put("value", "changed");
            document.clear();
            documents.clear();
            return accepted();
        });

        tools.executeMongoWrite(exchange, DATASOURCE_URI, "app", "INSERT_MANY", "orders",
            null, null, documents, null, null);

        ArgumentCaptor<DlExecuteParam> execution = ArgumentCaptor.forClass(DlExecuteParam.class);
        verify(dlTemplateService).execute(execution.capture());
        assertThat(execution.getValue().getSql())
            .isEqualTo("db.orders.insertMany([{\"nested\":{\"value\":\"reviewed\"}}])");
    }

    @ParameterizedTest
    @MethodSource("unconfirmedResponses")
    void unconfirmedWriteAndAdminNeverExecute(McpSchema.ElicitResult result) {
        when(exchange.createElicitation(any())).thenReturn(result);
        assertThat(runMutation(false)).contains("No database change was executed", "not confirmed");
        assertThat(runMutation(true)).contains("No database change was executed", "not confirmed");
        verify(dlTemplateService, never()).execute(any());
    }

    static Stream<Arguments> unconfirmedResponses() {
        return Stream.of(
            Arguments.of(new McpSchema.ElicitResult(McpSchema.ElicitResult.Action.DECLINE, Map.of())),
            Arguments.of(new McpSchema.ElicitResult(McpSchema.ElicitResult.Action.CANCEL, Map.of())),
            Arguments.of(new McpSchema.ElicitResult(McpSchema.ElicitResult.Action.ACCEPT, Map.of())),
            Arguments.of(new McpSchema.ElicitResult(McpSchema.ElicitResult.Action.ACCEPT, Map.of("confirm", false))),
            Arguments.of(new McpSchema.ElicitResult(McpSchema.ElicitResult.Action.ACCEPT, Map.of("confirm", "true"))),
            Arguments.of(new McpSchema.ElicitResult(McpSchema.ElicitResult.Action.ACCEPT, null)),
            Arguments.of((Object) null));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unsupportedClientsFailClosed(boolean admin) {
        when(exchange.getClientCapabilities()).thenReturn(new McpSchema.ClientCapabilities(null, null, null, null));
        assertThatThrownBy(() -> runMutation(admin)).hasMessageContaining("does not advertise elicitation capability");
        verify(dlTemplateService, never()).execute(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void transportFailureNeverExecutes(boolean admin) {
        when(exchange.createElicitation(any())).thenThrow(new IllegalStateException("client disconnected"));
        assertThatThrownBy(() -> runMutation(admin)).hasMessageContaining("client disconnected");
        verify(dlTemplateService, never()).execute(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void configuredClientWhitelistUsesSharedApprovalAndRetainsPermissionChecks(boolean admin) {
        when(configService.getApprovalClientKeywords()).thenReturn(List.of("trusted runner"));
        when(exchange.getClientInfo()).thenReturn(new McpSchema.Implementation("host", "Trusted Runner Desktop", "1"));
        when(exchange.getClientCapabilities()).thenReturn(null);
        assertThat(runMutation(admin)).contains("success: true");
        verify(exchange, never()).createElicitation(any());
        verify(dataSourceService, times(2)).queryExistent(eq(1L), any());
    }

    @Test
    void emptyWhitelistDoesNotImplicitlyApproveCodex() {
        when(exchange.getClientInfo()).thenReturn(new McpSchema.Implementation("codex", "Codex", "1"));
        when(exchange.getClientCapabilities()).thenReturn(null);
        assertThatThrownBy(() -> runMutation(false)).hasMessageContaining("does not advertise elicitation capability");
        verify(dlTemplateService, never()).execute(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void disabledPermissionBlocksBeforeApproval(boolean admin) {
        sharedPolicy.setAllowWrite(false);
        sharedPolicy.setAllowDdl(false);
        assertThatThrownBy(() -> runMutation(admin)).hasMessageContaining("disabled");
        verify(exchange, never()).createElicitation(any());
        verify(dlTemplateService, never()).execute(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void releaseDatasourceBlocksBeforeApprovalEvenForWhitelistedClient(boolean admin) {
        when(configService.getApprovalClientKeywords()).thenReturn(List.of("test-client"));
        dataSource.setEnvType("production");
        assertThatThrownBy(() -> runMutation(admin)).hasMessageContaining("Release/production");
        verify(exchange, never()).createElicitation(any());
        verify(dlTemplateService, never()).execute(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void permissionRevokedDuringApprovalBlocksExecution(boolean admin) {
        when(exchange.createElicitation(any())).thenAnswer(invocation -> {
            sharedPolicy.setAllowWrite(false);
            sharedPolicy.setAllowDdl(false);
            return accepted();
        });
        assertThatThrownBy(() -> runMutation(admin)).hasMessageContaining("disabled");
        verify(exchange).createElicitation(any());
        verify(dlTemplateService, never()).execute(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void datasourceChangedToReleaseDuringApprovalBlocksExecution(boolean admin) {
        when(exchange.createElicitation(any())).thenAnswer(invocation -> {
            dataSource.setEnvType("production");
            return accepted();
        });
        assertThatThrownBy(() -> runMutation(admin)).hasMessageContaining("Release/production");
        verify(exchange).createElicitation(any());
        verify(dlTemplateService, never()).execute(any());
    }

    @Test
    void reducedBatchLimitDuringApprovalBlocksExecution() {
        when(exchange.createElicitation(any())).thenAnswer(invocation -> {
            mongoConfig.setMaxBatchDocuments(1);
            return accepted();
        });
        assertThatThrownBy(() -> tools.executeMongoWrite(exchange, DATASOURCE_URI, "app", "INSERT_MANY", "orders",
            null, null, List.of(Map.of("id", 1), Map.of("id", 2)), null, null))
            .hasMessageContaining("max-batch-documents 1");
        verify(dlTemplateService, never()).execute(any());
    }

    @Test
    void multiWriteExceedingMaximumFailsBeforeApproval() {
        when(dlTemplateService.execute(any())).thenReturn(countResult(3));
        assertThatThrownBy(() -> tools.executeMongoWrite(exchange, DATASOURCE_URI, "app", "DELETE_MANY",
            "orders", Map.of("expired", true), null, null, null, 2)).hasMessageContaining("exceed maxAffectedDocuments 2");
        verify(exchange, never()).createElicitation(any());
        verifyOnlyCounts(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void multiWriteRecountsBeforeExecutionIncludingWhitelistedClients(boolean whitelisted) {
        if (whitelisted) {
            when(configService.getApprovalClientKeywords()).thenReturn(List.of("test-client"));
        }
        when(dlTemplateService.execute(any())).thenReturn(countResult(2), countResult(4));
        assertThatThrownBy(() -> tools.executeMongoWrite(exchange, DATASOURCE_URI, "app", "DELETE_MANY",
            "orders", Map.of("expired", true), null, null, null, 3))
            .hasMessageContaining("now exceed maxAffectedDocuments 3");
        verifyOnlyCounts(2);
    }

    @Test
    void returnsSampledMongoJsonSchema() {
        String schema = "[{\"collection\":\"orders\",\"source\":\"sampledDocuments\",\"schema\":{}}]";
        when(databaseService.queryMongoSchema(1L, "app", List.of("orders"))).thenReturn(schema);
        assertThat(tools.getMongoCollectionSchema(DATASOURCE_URI, "app", List.of("orders"))).isEqualTo(schema);
    }

    @Test
    void mcpSchemaExposesFourToolsAndInjectsExchangeIntoWriteAndAdminHandlers() {
        List<SyncToolSpecification> specifications = SyncMcpAnnotationProviders.toolSpecifications(List.of(tools));
        assertThat(specifications).extracting(specification -> specification.tool().name())
            .containsExactlyInAnyOrder("executeMongoQuery", "getMongoCollectionSchema", "executeMongoWrite",
                "executeMongoAdmin");
        for (String name : List.of("executeMongoWrite", "executeMongoAdmin")) {
            SyncToolSpecification specification = specifications.stream()
                .filter(item -> item.tool().name().equals(name)).findFirst().orElseThrow();
            assertThat(specification.tool().annotations().destructiveHint()).isTrue();
            assertThat(specification.tool().annotations().readOnlyHint()).isFalse();
            assertThat(specification.tool().inputSchema().properties())
                .doesNotContainKeys("exchange", "operationId", "confirmationText");
            Map<String, Object> arguments = new LinkedHashMap<>();
            arguments.put("datasourceUri", DATASOURCE_URI);
            arguments.put("databaseName", "app");
            arguments.put("operation", name.equals("executeMongoWrite") ? "INSERT_ONE" : "DROP_DATABASE");
            if (name.equals("executeMongoWrite")) {
                arguments.put("collection", "orders");
                arguments.put("document", Map.of("id", 1));
            }
            specification.callHandler().apply(exchange, new McpSchema.CallToolRequest(name, arguments));
        }
        verify(exchange, times(2)).createElicitation(any());
        ArgumentCaptor<DlExecuteParam> executions = ArgumentCaptor.forClass(DlExecuteParam.class);
        verify(dlTemplateService, times(2)).execute(executions.capture());
        assertThat(executions.getAllValues()).extracting(DlExecuteParam::getSql)
            .containsExactly("db.orders.insertOne({\"id\":1})", "db.dropDatabase()");
    }

    private String runMutation(boolean admin) {
        return admin ? tools.executeMongoAdmin(exchange, DATASOURCE_URI, "app", "DROP_DATABASE", null, null, null)
            : tools.executeMongoWrite(exchange, DATASOURCE_URI, "app", "INSERT_ONE", "orders", null,
                Map.of("id", 1), null, null, null);
    }

    private void verifyOnlyCounts(int count) {
        ArgumentCaptor<DlExecuteParam> executions = ArgumentCaptor.forClass(DlExecuteParam.class);
        verify(dlTemplateService, times(count)).execute(executions.capture());
        assertThat(executions.getAllValues())
            .allSatisfy(param -> assertThat(param.getSql()).contains(".countDocuments("));
    }

    private McpSchema.ElicitResult accepted() {
        return new McpSchema.ElicitResult(McpSchema.ElicitResult.Action.ACCEPT, Map.of("confirm", true));
    }

    private List<ExecuteResult> successResult() {
        return List.of(ExecuteResult.builder().success(true).updateCount(1).build());
    }

    private List<ExecuteResult> countResult(long count) {
        return List.of(ExecuteResult.builder().success(true)
            .headerList(List.of(Header.builder().name("count").build()))
            .dataList(List.of(List.of(Long.toString(count)))).build());
    }
}
