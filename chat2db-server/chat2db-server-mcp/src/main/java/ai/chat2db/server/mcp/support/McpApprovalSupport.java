package ai.chat2db.server.mcp.support;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import ai.chat2db.server.mcp.config.McpServerConfigService;
import ai.chat2db.server.tools.common.util.I18nUtils;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/** SQL、MongoDB、Redis 共用的客户端审批与 elicitation 入口。 */
@Component
public class McpApprovalSupport {

    private static final String CONFIRM_FIELD = "confirm";

    private final McpServerConfigService configService;

    public McpApprovalSupport(McpServerConfigService configService) {
        this.configService = configService;
    }

    public ApprovalResult confirm(McpSyncServerExchange exchange, String operationType,
                                  String target, String preview) {
        if (usesClientApproval(exchange)) {
            return new ApprovalResult(true, null);
        }
        McpSchema.ClientCapabilities capabilities = exchange == null ? null : exchange.getClientCapabilities();
        if (capabilities == null || capabilities.elicitation() == null) {
            throw new IllegalStateException(I18nUtils.getMessage("mcp.approval.unsupported"));
        }
        McpSchema.ElicitResult result = exchange.createElicitation(McpSchema.ElicitRequest.builder()
            .message(I18nUtils.getMessage("mcp.approval.request", new Object[] {operationType, target, preview}))
            .requestedSchema(Map.of(
                "type", "object",
                "properties", Map.of(CONFIRM_FIELD, Map.of(
                    "type", "boolean",
                    "title", I18nUtils.getMessage("mcp.approval.title"),
                    "description", I18nUtils.getMessage("mcp.approval.description"),
                    "default", false)),
                "required", List.of(CONFIRM_FIELD)))
            .build());
        // 接受表单且明确勾选确认才执行；拒绝、取消、空响应或字符串 "true" 均不算确认。
        if (result != null && result.action() == McpSchema.ElicitResult.Action.ACCEPT
            && result.content() != null && Boolean.TRUE.equals(result.content().get(CONFIRM_FIELD))) {
            return new ApprovalResult(true, null);
        }
        String action = result == null || result.action() == null ? "unknown"
            : result.action().name().toLowerCase(Locale.ROOT);
        return new ApprovalResult(false,
            I18nUtils.getMessage("mcp.approval.notConfirmed", new Object[] {operationType, action}));
    }

    private boolean usesClientApproval(McpSyncServerExchange exchange) {
        McpSchema.Implementation client = exchange == null ? null : exchange.getClientInfo();
        if (client == null) {
            return false;
        }
        // 客户端自报名称只用于审批兼容，不是身份认证；工具仍须检查权限、环境和执行参数。
        return configService.getApprovalClientKeywords().stream().filter(StringUtils::isNotBlank)
            .map(String::trim).anyMatch(keyword -> StringUtils.containsIgnoreCase(client.name(), keyword)
                || StringUtils.containsIgnoreCase(client.title(), keyword));
    }

    public record ApprovalResult(boolean approved, String message) {
    }
}
