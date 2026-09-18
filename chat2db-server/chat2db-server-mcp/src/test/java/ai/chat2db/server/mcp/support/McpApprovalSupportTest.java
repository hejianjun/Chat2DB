package ai.chat2db.server.mcp.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import ai.chat2db.server.mcp.config.McpServerConfigService;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.i18n.LocaleContextHolder;

class McpApprovalSupportTest {

    private McpServerConfigService config;
    private McpSyncServerExchange exchange;
    private McpApprovalSupport support;

    @BeforeEach
    void setUp() {
        McpTestSupport.initializeMessages();
        config = mock(McpServerConfigService.class);
        when(config.getApprovalClientKeywords()).thenReturn(List.of("codex"));
        exchange = mock(McpSyncServerExchange.class);
        when(exchange.getClientInfo()).thenReturn(new McpSchema.Implementation("other-host", "1"));
        when(exchange.getClientCapabilities()).thenReturn(new McpSchema.ClientCapabilities(
            null, null, null, new McpSchema.ClientCapabilities.Elicitation()));
        support = new McpApprovalSupport(config);
    }

    @Test
    void clientNameAndTitleUseCaseInsensitiveKeywordMatching() {
        when(exchange.getClientCapabilities()).thenReturn(null);
        when(exchange.getClientInfo()).thenReturn(new McpSchema.Implementation("OpenAI CODEX CLI", "1"));
        assertThat(confirm().approved()).isTrue();

        when(config.getApprovalClientKeywords()).thenReturn(List.of("trusted desktop"));
        when(exchange.getClientInfo()).thenReturn(new McpSchema.Implementation("host", "My Trusted Desktop App", "1"));
        assertThat(confirm().approved()).isTrue();
        verify(exchange, never()).createElicitation(any());
    }

    @Test
    void clearingWhitelistTakesEffectOnNextRequest() {
        when(exchange.getClientInfo()).thenReturn(new McpSchema.Implementation("codex", "1"));
        assertThat(confirm().approved()).isTrue();

        when(config.getApprovalClientKeywords()).thenReturn(List.of());
        when(exchange.createElicitation(any())).thenReturn(
            new McpSchema.ElicitResult(McpSchema.ElicitResult.Action.DECLINE, Map.of()));

        assertThat(confirm().approved()).isFalse();
        verify(exchange).createElicitation(any());
    }

    @Test
    void blankKeywordsCannotMatchAllClients() {
        when(config.getApprovalClientKeywords()).thenReturn(Arrays.asList(null, "", "  "));
        when(exchange.getClientCapabilities()).thenReturn(null);

        assertThatThrownBy(this::confirm).hasMessageContaining("does not advertise elicitation capability");
    }

    @Test
    void missingExchangeOrClientInformationCannotBypassApproval() {
        assertThatThrownBy(() -> support.confirm(null, "DELETE", "test/key", "{}"))
            .hasMessageContaining("does not advertise elicitation capability");
        when(exchange.getClientInfo()).thenReturn(null);
        assertThat(confirm().approved()).isFalse();
        verify(exchange).createElicitation(any());
    }

    @Test
    void acceptRequiresBooleanTrue() {
        when(exchange.createElicitation(any())).thenReturn(
            new McpSchema.ElicitResult(McpSchema.ElicitResult.Action.ACCEPT, Map.of("confirm", true)));
        assertThat(confirm().approved()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("unconfirmedResponses")
    void allUnconfirmedResponsesAreRejected(McpSchema.ElicitResult response) {
        when(exchange.createElicitation(any())).thenReturn(response);
        var result = confirm();
        assertThat(result.approved()).isFalse();
        assertThat(result.message()).contains("No database change was executed").contains("not confirmed");
    }

    static Stream<McpSchema.ElicitResult> unconfirmedResponses() {
        return Stream.of(null,
            new McpSchema.ElicitResult(McpSchema.ElicitResult.Action.DECLINE, Map.of("confirm", true)),
            new McpSchema.ElicitResult(McpSchema.ElicitResult.Action.CANCEL, Map.of("confirm", true)),
            new McpSchema.ElicitResult(McpSchema.ElicitResult.Action.ACCEPT, null),
            new McpSchema.ElicitResult(McpSchema.ElicitResult.Action.ACCEPT, Map.of()),
            new McpSchema.ElicitResult(McpSchema.ElicitResult.Action.ACCEPT, Map.of("confirm", false)),
            new McpSchema.ElicitResult(McpSchema.ElicitResult.Action.ACCEPT, Map.of("confirm", "true")));
    }

    @Test
    void elicitationFailureCannotFallBackToApproval() {
        when(exchange.createElicitation(any())).thenThrow(new IllegalStateException("timeout"));
        assertThatThrownBy(this::confirm).hasMessageContaining("timeout");
    }

    @Test
    void confirmationMessagesUseCurrentLocale() {
        try {
            LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
            assertThat(confirm().message()).contains("未执行数据库变更");
        } finally {
            LocaleContextHolder.setLocale(Locale.US);
        }
    }

    private McpApprovalSupport.ApprovalResult confirm() {
        return support.confirm(exchange, "DELETE", "test/key", "{\"key\":\"key\"}");
    }
}
