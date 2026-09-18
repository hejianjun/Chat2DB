package ai.chat2db.server.mcp.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.util.List;

import ai.chat2db.server.domain.api.service.ConfigService;
import ai.chat2db.server.mcp.config.McpServerConfigService.McpServerConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class McpServerConfigServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private McpProperties properties;
    private McpServerConfigService service;

    @BeforeEach
    void setUp() {
        properties = new McpProperties();
        service = spy(new McpServerConfigService(properties, mock(ConfigService.class), mapper));
        // 仅模拟配置读取，避免单元测试初始化真实的本地 H2 配置库。
        doReturn(null).when(service).getStoredConfig();
    }

    @Test
    void missingStoredConfigOrLegacyFieldKeepsDefaultCodex() throws Exception {
        assertThat(service.getApprovalClientKeywords()).containsExactly("codex");
        stored("{\"authType\":\"none\",\"sql\":{\"allowWrite\":true}}");
        assertThat(service.getApprovalClientKeywords()).containsExactly("codex");
        assertThat(service.getEffectiveSql().isAllowWrite()).isTrue();
        stored("{\"approvalClientKeywords\":null}");
        assertThat(service.getApprovalClientKeywords()).containsExactly("codex");
    }

    @Test
    void storedEmptyListDisablesHostApprovalEvenWithPropertyDefault() throws Exception {
        stored("{\"approvalClientKeywords\":[]}");
        assertThat(service.getApprovalClientKeywords()).isEmpty();
    }

    @Test
    void keywordsNormalizeWithoutLosingOtherConfiguration() throws Exception {
        stored("""
            {"authType":"bearer", "token":"test-token", "futureSetting":true,
             "sql":{"allowWrite":true,"allowDdl":true}, "mongodb":{"maxBatchDocuments":25},
             "approvalClientKeywords":[" CODEX ", "codex", "", "  ", null, "Other Host"]}
            """);
        assertThat(service.getApprovalClientKeywords()).containsExactly("codex", "other host");
        assertThat(service.getEffectiveToken()).isEqualTo("test-token");
        assertThat(service.getEffectiveSql().isAllowDdl()).isTrue();
        assertThat(service.getEffectiveMongo().getMaxBatchDocuments()).isEqualTo(25);
    }

    @Test
    void propertyAndRuntimeOverridesAreReadOnEveryRequest() throws Exception {
        properties.setApprovalClientKeywords(List.of("LOCAL CLIENT"));
        assertThat(service.getApprovalClientKeywords()).containsExactly("local client");
        stored("{\"approvalClientKeywords\":[\"desktop\"]}");
        assertThat(service.getApprovalClientKeywords()).containsExactly("desktop");
        stored("{\"approvalClientKeywords\":[]}");
        assertThat(service.getApprovalClientKeywords()).isEmpty();
    }

    private void stored(String json) throws Exception {
        doReturn(mapper.readValue(json, McpServerConfig.class)).when(service).getStoredConfig();
    }
}
