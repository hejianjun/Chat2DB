package ai.chat2db.server.mcp.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import ai.chat2db.server.domain.api.model.DataSource;
import ai.chat2db.server.domain.api.model.Environment;
import ai.chat2db.server.domain.api.param.datasource.DataSourcePageQueryParam;
import ai.chat2db.server.domain.api.param.datasource.DataSourceSelector;
import ai.chat2db.server.domain.api.service.DataSourceService;
import ai.chat2db.server.tools.base.wrapper.ServicePage;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class DataSourceResourceServiceTest {

    private DataSourceService dataSourceService;
    private DataSourceResourceService dataSourceResourceService;

    @BeforeEach
    void setUp() {
        dataSourceService = org.mockito.Mockito.mock(DataSourceService.class);
        dataSourceResourceService = new DataSourceResourceService(dataSourceService, new ObjectMapper(), Runnable::run);
    }

    @Test
    void resolveLoadsDatasourceFromResourceUri() {
        DataSource dataSource = dataSource();
        when(dataSourceService.queryExistent(eq(1L), any(DataSourceSelector.class))).thenReturn(dataSource);

        ResolvedDataSource resolvedDataSource = dataSourceResourceService.resolve("chat2db://datasources/1");

        assertThat(resolvedDataSource.getDataSourceId()).isEqualTo(1L);
        assertThat(resolvedDataSource.getDataSource()).isSameAs(dataSource);
        assertThat(resolvedDataSource.getSummary()).isEqualTo("id=1;name=orders;env=prod");
        verify(dataSourceService).queryExistent(eq(1L), any(DataSourceSelector.class));
    }

    @Test
    void resourceUriUsesDatasourceId() {
        DataSource dataSource = dataSource();
        dataSource.setAlias("orders prod/primary");
        when(dataSourceService.queryExistent(eq(1L), any(DataSourceSelector.class))).thenReturn(dataSource);

        ResolvedDataSource resolvedDataSource = dataSourceResourceService.resolve("chat2db://datasources/1");

        assertThat(resolvedDataSource.getDataSourceId()).isEqualTo(1L);
        assertThat(dataSourceResourceService.uri(dataSource)).isEqualTo("chat2db://datasources/1");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "dsref:v1:id=1",
        "chat2db://wrong/1",
        "chat2db://datasources/1/extra",
        "chat2db://datasources/orders",
        "chat2db://datasources/1?x=1",
        "chat2db://datasources/1#fragment"
    })
    void resolveRejectsInvalidResourceUri(String datasourceUri) {
        assertThatThrownBy(() -> dataSourceResourceService.resolve(datasourceUri))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid datasourceUri. Select a datasource resource first.");
    }

    @Test
    void resourceReadReturnsSafeDatasourceMetadataOnly() {
        DataSource dataSource = dataSource();
        when(dataSourceService.queryExistent(eq(1L), any(DataSourceSelector.class))).thenReturn(dataSource);

        McpSchema.ReadResourceResult result = dataSourceResourceService.resourceTemplateSpecifications()
            .get(0)
            .readHandler()
            .apply(null, new McpSchema.ReadResourceRequest("chat2db://datasources/1"));

        String text = ((McpSchema.TextResourceContents) result.contents().get(0)).text();
        assertThat(text).contains("\"uri\":\"chat2db://datasources/1\"");
        assertThat(text).contains("\"type\":\"MYSQL\"");
        assertThat(text).contains("\"description\":\"alias=orders;type=MYSQL;env=prod;kind=\"");
        assertThat(text).doesNotContain("secret");
        assertThat(text).doesNotContain("jdbc:mysql");
        assertThat(text).doesNotContain("root");
        assertThat(text).doesNotContain("password");
        assertThat(text).doesNotContain("url");
        assertThat(text).doesNotContain("userName");
    }

    @Test
    void resourceListReturnsDatasourceResources() {
        when(dataSourceService.queryPage(any(DataSourcePageQueryParam.class), any(DataSourceSelector.class)))
            .thenReturn(ServicePage.of(List.of(dataSource()), 1L, 1, 1000));

        List<io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification> resources =
            dataSourceResourceService.resourceSpecifications();

        assertThat(resources).hasSize(1);
        assertThat(resources.get(0).resource().uri()).isEqualTo("chat2db://datasources/1");
        assertThat(resources.get(0).resource().name()).isEqualTo("orders");

        ArgumentCaptor<DataSourcePageQueryParam> paramCaptor = ArgumentCaptor.forClass(DataSourcePageQueryParam.class);
        verify(dataSourceService).queryPage(paramCaptor.capture(), any(DataSourceSelector.class));
        assertThat(paramCaptor.getValue().getPageNo()).isEqualTo(1);
        assertThat(paramCaptor.getValue().getPageSize()).isEqualTo(1000);
    }

    @Test
    void resolveRejectsNonNumericId() {
        assertThatThrownBy(() -> dataSourceResourceService.resolve("chat2db://datasources/missing"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid datasourceUri. Select a datasource resource first.");
    }

    private DataSource dataSource() {
        DataSource dataSource = new DataSource();
        dataSource.setId(1L);
        dataSource.setAlias("orders");
        dataSource.setType("MYSQL");
        dataSource.setUrl("jdbc:mysql://localhost:3306/orders");
        dataSource.setUserName("root");
        dataSource.setPassword("secret");
        dataSource.setEnvironment(Environment.builder().shortName("prod").name("Production").build());
        return dataSource;
    }
}
