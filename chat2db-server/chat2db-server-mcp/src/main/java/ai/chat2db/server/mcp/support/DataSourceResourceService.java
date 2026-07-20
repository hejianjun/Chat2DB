package ai.chat2db.server.mcp.support;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import ai.chat2db.server.domain.api.model.DataSource;
import ai.chat2db.server.domain.api.param.datasource.DataSourcePageQueryParam;
import ai.chat2db.server.domain.api.param.datasource.DataSourceSelector;
import ai.chat2db.server.domain.api.service.DataSourceService;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.tools.base.wrapper.ServicePage;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Exposes Chat2DB datasources as MCP resources and resolves datasource resource URIs for tools.
 */
@Component
public class DataSourceResourceService {

    private static final String SCHEME = "chat2db";
    private static final String HOST = "datasources";
    private static final String CATALOG_URI = "chat2db://datasources";
    private static final String DATASOURCE_URI_TEMPLATE = "chat2db://datasources/{dataSourceId}";
    private static final String MIME_TYPE = "application/json";
    private static final String INVALID_DATASOURCE_URI =
        "Invalid datasourceUri. Select a datasource resource first.";

    private final DataSourceService dataSourceService;
    private final ObjectMapper objectMapper;
    private final Consumer<Runnable> sessionRunner;

    @Autowired
    public DataSourceResourceService(DataSourceService dataSourceService, ObjectMapper objectMapper) {
        this(dataSourceService, objectMapper, Dbutils::runWithSession);
    }

    DataSourceResourceService(DataSourceService dataSourceService, ObjectMapper objectMapper,
        Consumer<Runnable> sessionRunner) {
        this.dataSourceService = dataSourceService;
        this.objectMapper = objectMapper;
        this.sessionRunner = sessionRunner;
    }

    public List<McpServerFeatures.SyncResourceSpecification> resourceSpecifications() {
        return queryDatasources(null).stream()
            .collect(Collectors.toMap(this::uri, dataSource -> dataSource, (left, right) -> left, LinkedHashMap::new))
            .values()
            .stream()
            .map(dataSource -> new McpServerFeatures.SyncResourceSpecification(
                McpSchema.Resource.builder()
                    .uri(uri(dataSource))
                    .name(StringUtils.defaultString(dataSource.getAlias()))
                    .description(resourceDescription(dataSource))
                    .mimeType(MIME_TYPE)
                    .build(),
                (exchange, request) -> readDatasource(request.uri())))
            .collect(Collectors.toList());
    }

    public List<McpServerFeatures.SyncResourceTemplateSpecification> resourceTemplateSpecifications() {
        return List.of(new McpServerFeatures.SyncResourceTemplateSpecification(
            McpSchema.ResourceTemplate.builder()
                .uriTemplate(DATASOURCE_URI_TEMPLATE)
                .name("Chat2DB datasource")
                .description("Safe metadata for a Chat2DB datasource resource.")
                .mimeType(MIME_TYPE)
                .build(),
            (exchange, request) -> readDatasource(request.uri())));
    }

    public ResolvedDataSource resolve(String datasourceUri) {
        Long dataSourceId = parseDataSourceId(datasourceUri);
        DataSource dataSource = queryExistent(dataSourceId);
        return ResolvedDataSource.builder()
            .dataSourceId(dataSourceId)
            .dataSource(dataSource)
            .summary(summary(dataSource))
            .build();
    }

    public String uri(DataSource dataSource) {
        if (dataSource == null || dataSource.getId() == null) {
            throw new IllegalArgumentException(INVALID_DATASOURCE_URI);
        }
        // Keep the URI stable across datasource renames; alias stays in the resource name for readability.
        return CATALOG_URI + "/" + dataSource.getId();
    }

    public String summary(DataSource dataSource) {
        String dataSourceId = Objects.toString(dataSource.getId(), "unknown");
        return String.format(Locale.ROOT, "id=%s;name=%s;env=%s",
            dataSourceId,
            Objects.toString(dataSource.getAlias(), dataSourceId),
            environmentText(dataSource));
    }

    private List<DataSource> queryDatasources(String searchKey) {
        AtomicReference<List<DataSource>> result = new AtomicReference<>(List.of());
        sessionRunner.accept(() -> {
            DataSourcePageQueryParam param = new DataSourcePageQueryParam();
            param.setSearchKey(searchKey);
            param.setPageNo(1);
            param.setPageSize(1000);
            ServicePage<DataSource> page = dataSourceService.queryPage(param,
                DataSourceSelector.builder().environment(true).build());
            result.set(page.getData() == null ? List.of() : page.getData());
        });
        return result.get();
    }

    private McpSchema.ReadResourceResult readDatasource(String datasourceUri) {
        ResolvedDataSource resolvedDataSource = resolve(datasourceUri);
        return textResult(datasourceUri, toJson(safeResourceRow(resolvedDataSource.getDataSource())));
    }

    private McpSchema.ReadResourceResult textResult(String uri, String text) {
        return new McpSchema.ReadResourceResult(List.of(new McpSchema.TextResourceContents(uri, MIME_TYPE, text)));
    }

    private Map<String, Object> safeResourceRow(DataSource dataSource) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("uri", uri(dataSource));
        row.put("id", dataSource.getId());
        row.put("name", StringUtils.defaultString(dataSource.getAlias()));
        row.put("environment", environmentText(dataSource));
        row.put("type", StringUtils.defaultString(dataSource.getType()));
        // Only expose non-secret metadata here; connection details remain internal to tool execution.
        row.put("description", description(dataSource));
        return row;
    }

    private DataSource queryExistent(Long dataSourceId) {
        AtomicReference<DataSource> result = new AtomicReference<>();
        sessionRunner.accept(() -> result.set(dataSourceService.queryExistent(dataSourceId,
            DataSourceSelector.builder().environment(true).build())));
        return result.get();
    }

    private Long parseDataSourceId(String datasourceUri) {
        if (StringUtils.isBlank(datasourceUri)) {
            throw new IllegalArgumentException(INVALID_DATASOURCE_URI);
        }
        try {
            URI uri = URI.create(datasourceUri);
            if (!SCHEME.equals(uri.getScheme()) || !HOST.equals(uri.getHost())
                || StringUtils.isNotBlank(uri.getRawQuery()) || StringUtils.isNotBlank(uri.getRawFragment())) {
                throw new IllegalArgumentException(INVALID_DATASOURCE_URI);
            }
            String path = StringUtils.defaultString(uri.getRawPath());
            if (!path.matches("/\\d+")) {
                throw new IllegalArgumentException(INVALID_DATASOURCE_URI);
            }
            return Long.valueOf(path.substring(1));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(INVALID_DATASOURCE_URI, e);
        }
    }

    private String environmentText(DataSource dataSource) {
        if (dataSource.getEnvironment() != null) {
            return StringUtils.defaultIfBlank(dataSource.getEnvironment().getShortName(),
                StringUtils.defaultString(dataSource.getEnvironment().getName()));
        }
        return StringUtils.defaultString(dataSource.getEnvType());
    }

    private String resourceDescription(DataSource dataSource) {
        String environment = environmentText(dataSource);
        return StringUtils.isBlank(environment) ? null : "env=" + environment;
    }

    private String description(DataSource dataSource) {
        return StringUtils.defaultString(dataSource.getUrl());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to format datasource resource as JSON.", e);
        }
    }

}
