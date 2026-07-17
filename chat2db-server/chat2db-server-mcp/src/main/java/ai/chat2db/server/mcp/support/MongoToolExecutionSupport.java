package ai.chat2db.server.mcp.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ai.chat2db.server.domain.api.param.DlExecuteParam;
import ai.chat2db.server.domain.api.service.DlTemplateService;
import ai.chat2db.spi.model.ExecuteResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Canonical MQL generation and execution shared by MongoDB MCP tools.
 */
@Component
public class MongoToolExecutionSupport {

    private final DlTemplateService dlTemplateService;
    private final ObjectMapper objectMapper;

    public MongoToolExecutionSupport(DlTemplateService dlTemplateService, ObjectMapper objectMapper) {
        this.dlTemplateService = dlTemplateService;
        this.objectMapper = objectMapper;
    }

    public List<ExecuteResult> execute(Long dataSourceId, String databaseName, String mql,
                                      Integer pageNo, Integer pageSize) {
        List<ExecuteResult> results = dlTemplateService.execute(executeParam(dataSourceId, databaseName, mql,
            pageNo, pageSize));
        requireSuccess(results);
        return results;
    }

    public long count(Long dataSourceId, String databaseName, String countMql) {
        List<ExecuteResult> results = execute(dataSourceId, databaseName, countMql, 1, 1);
        ExecuteResult result = results.get(0);
        if (CollectionUtils.isEmpty(result.getDataList()) || CollectionUtils.isEmpty(result.getDataList().get(0))) {
            throw new IllegalStateException("MongoDB countDocuments returned no count.");
        }
        try {
            return Long.parseLong(result.getDataList().get(0).get(0));
        } catch (NumberFormatException e) {
            throw new IllegalStateException("MongoDB countDocuments returned an invalid count.", e);
        }
    }

    public String collectionReference(String collection) {
        if (StringUtils.isBlank(collection)) {
            throw new IllegalArgumentException("collection must not be blank.");
        }
        if (collection.matches("^[A-Za-z_$][\\w$]*$")) {
            return "db." + collection;
        }
        return "db.getCollection(" + json(collection) + ")";
    }

    public String json(Object value) {
        try {
            return objectMapper.writeValueAsString(canonicalize(value));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("MongoDB document cannot be serialized as Extended JSON.", e);
        }
    }

    public String countMql(String collection, Map<String, Object> filter) {
        return collectionReference(collection) + ".countDocuments(" + json(filter) + ")";
    }

    private Object canonicalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            // MongoDB embedded-document equality can depend on field order, so normalization must preserve it.
            map.forEach((key, item) -> normalized.put(String.valueOf(key), canonicalize(item)));
            return normalized;
        }
        if (value instanceof List<?> list) {
            List<Object> canonical = new ArrayList<>(list.size());
            list.forEach(item -> canonical.add(canonicalize(item)));
            return canonical;
        }
        return value;
    }

    private DlExecuteParam executeParam(Long dataSourceId, String databaseName, String mql,
                                        Integer pageNo, Integer pageSize) {
        DlExecuteParam param = new DlExecuteParam();
        param.setDataSourceId(dataSourceId);
        param.setDatabaseName(databaseName);
        param.setConsoleId(0L);
        param.setSql(mql);
        param.setPageNo(pageNo == null ? 1 : pageNo);
        param.setPageSize(pageSize == null ? 100 : pageSize);
        param.setPageSizeAll(false);
        return param;
    }

    private void requireSuccess(List<ExecuteResult> results) {
        if (CollectionUtils.isEmpty(results)) {
            throw new IllegalStateException("MongoDB execution returned no result.");
        }
        ExecuteResult failure = results.stream()
            .filter(result -> !Boolean.TRUE.equals(result.getSuccess()))
            .findFirst()
            .orElse(null);
        if (failure != null) {
            throw new IllegalStateException(
                StringUtils.defaultIfBlank(failure.getMessage(), "MongoDB execution failed."));
        }
    }
}
