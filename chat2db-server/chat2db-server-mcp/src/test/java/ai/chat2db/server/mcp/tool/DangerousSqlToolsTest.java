package ai.chat2db.server.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import ai.chat2db.server.domain.api.service.DlTemplateService;
import ai.chat2db.server.mcp.support.ConnectionScope;
import ai.chat2db.server.mcp.support.ResolvedDataSource;
import ai.chat2db.server.mcp.support.ResultFormatter;
import ai.chat2db.server.mcp.support.SqlGuard;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DangerousSqlToolsTest {

    private final ResolvedDataSource resolvedDataSource = ResolvedDataSource.builder()
        .dataSourceId(1L)
        .summary("id=1;name=dev;env=dev")
        .build();

    private ConnectionScope connectionScope;
    private DlTemplateService dlTemplateService;
    private ResultFormatter resultFormatter;
    private SqlGuard sqlGuard;
    private McpSyncServerExchange exchange;
    private DangerousSqlTools tools;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        connectionScope = mock(ConnectionScope.class);
        when(connectionScope.run(anyString(), anyString(), isNull(), any(Function.class)))
            .thenAnswer(invocation -> ((Function<ResolvedDataSource, Object>) invocation.getArgument(3))
                .apply(resolvedDataSource));

        dlTemplateService = mock(DlTemplateService.class);
        resultFormatter = mock(ResultFormatter.class);
        when(resultFormatter.format(anyList())).thenReturn("executed");
        sqlGuard = mock(SqlGuard.class);
        exchange = mock(McpSyncServerExchange.class);
        when(exchange.getClientCapabilities()).thenReturn(new McpSchema.ClientCapabilities(
            null, null, null, new McpSchema.ClientCapabilities.Elicitation()));

        tools = new DangerousSqlTools(dlTemplateService, connectionScope, resultFormatter, sqlGuard);
    }

    @Test
    @SuppressWarnings("unchecked")
    void acceptedDmlElicitationExecutesAndContainsExactSqlPreview() {
        when(exchange.createElicitation(any(McpSchema.ElicitRequest.class))).thenReturn(
            new McpSchema.ElicitResult(McpSchema.ElicitResult.Action.ACCEPT, Map.of("confirm", true)));

        String sql = "UPDATE orders SET status = 'DONE' WHERE id = 1";
        String result = tools.executeUpdate(exchange, "chat2db://datasources/1", "app", null,
            List.of(sql), List.of(1));

        assertThat(result).isEqualTo("executed");
        ArgumentCaptor<McpSchema.ElicitRequest> requestCaptor = ArgumentCaptor.forClass(McpSchema.ElicitRequest.class);
        verify(exchange).createElicitation(requestCaptor.capture());
        assertThat(requestCaptor.getValue().message()).contains(sql).contains("id=1;name=dev;env=dev/app");
        Map<String, Object> requestedSchema = requestCaptor.getValue().requestedSchema();
        assertThat(requestedSchema).containsEntry("type", "object");
        Map<String, Object> properties = (Map<String, Object>) requestedSchema.get("properties");
        assertThat(properties).containsKey("confirm");
        Map<String, Object> confirmation = (Map<String, Object>) properties.get("confirm");
        assertThat(confirmation).containsEntry("type", "boolean");
        assertThat(requestedSchema.get("required")).isEqualTo(List.of("confirm"));
        verify(dlTemplateService).executeBatchUpdate(any());
    }

    @Test
    void declinedDmlElicitationDoesNotExecute() {
        when(exchange.createElicitation(any(McpSchema.ElicitRequest.class))).thenReturn(
            new McpSchema.ElicitResult(McpSchema.ElicitResult.Action.DECLINE, Map.of()));

        String result = tools.executeUpdate(exchange, "chat2db://datasources/1", "app", null,
            List.of("DELETE FROM orders WHERE id = 1"), List.of(1));

        assertThat(result).contains("No SQL was executed").contains("decline");
        verify(dlTemplateService, never()).executeBatchUpdate(any());
    }

    @Test
    void acceptedDdlElicitationExecutes() {
        when(exchange.createElicitation(any(McpSchema.ElicitRequest.class))).thenReturn(
            new McpSchema.ElicitResult(McpSchema.ElicitResult.Action.ACCEPT, Map.of("confirm", true)));

        String result = tools.executeDdl(exchange, "chat2db://datasources/1", "app", null,
            "ALTER TABLE orders ADD COLUMN archived BOOLEAN");

        assertThat(result).isEqualTo("executed");
        verify(exchange).createElicitation(any(McpSchema.ElicitRequest.class));
        verify(dlTemplateService).execute(any());
    }

    @Test
    void missingElicitationCapabilityFailsClosed() {
        when(exchange.getClientCapabilities()).thenReturn(
            new McpSchema.ClientCapabilities(null, null, null, null));

        assertThatThrownBy(() -> tools.executeDdl(exchange, "chat2db://datasources/1", "app", null,
            "DROP TABLE orders"))
            .hasMessageContaining("does not advertise elicitation capability");
        verify(dlTemplateService, never()).execute(any());
    }

    @Test
    void elicitationSchemaIsPartOfDirectDmlToolAndLegacyPrepareToolsAreGone() {
        List<McpServerFeatures.SyncToolSpecification> specifications =
            org.springframework.ai.mcp.annotation.spring.SyncMcpAnnotationProviders.toolSpecifications(List.of(tools));

        assertThat(specifications).extracting(specification -> specification.tool().name())
            .containsExactlyInAnyOrder("executeUpdate", "executeDdl");
        assertThat(specifications).noneMatch(specification -> specification.tool().name().contains("Prepare"));

        McpServerFeatures.SyncToolSpecification dml = specifications.stream()
            .filter(specification -> specification.tool().name().equals("executeUpdate"))
            .findFirst()
            .orElseThrow();
        Map<String, Object> arguments = Map.of(
            "datasourceUri", "chat2db://datasources/1",
            "databaseName", "app",
            "sqlList", List.of("UPDATE orders SET status = 'DONE' WHERE id = 1"),
            "expectedUpdateCounts", List.of(1));
        when(exchange.createElicitation(any(McpSchema.ElicitRequest.class))).thenReturn(
            new McpSchema.ElicitResult(McpSchema.ElicitResult.Action.DECLINE, Map.of()));

        dml.callHandler().apply(exchange, new McpSchema.CallToolRequest("executeUpdate", arguments));

        verify(exchange).createElicitation(any(McpSchema.ElicitRequest.class));
        verify(dlTemplateService, never()).executeBatchUpdate(any());
    }
}
