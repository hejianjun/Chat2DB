package ai.chat2db.server.mcp.support;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import ai.chat2db.server.domain.api.model.DataSource;
import ai.chat2db.server.domain.api.param.datasource.DataSourceSelector;
import ai.chat2db.server.domain.api.service.DataSourceService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Issues and verifies signed datasource references for MCP tools.
 */
@Component
public class DataSourceRefService {

    private static final String REF_PREFIX = "dsref:v1:";
    private static final String SIGN_ALGORITHM = "HmacSHA256";
    private static final long REF_TTL_SECONDS = 30 * 60;

    private final DataSourceService dataSourceService;
    private final byte[] secret;

    public DataSourceRefService(DataSourceService dataSourceService) {
        this.dataSourceService = dataSourceService;
        this.secret = new byte[32];
        new SecureRandom().nextBytes(this.secret);
    }

    public String issue(DataSource dataSource) {
        long expiresAt = Instant.now().plusSeconds(REF_TTL_SECONDS).getEpochSecond();
        String payload = canonicalPayload(dataSource, expiresAt);
        return REF_PREFIX + payload + ";sig=" + sign(payload);
    }

    public ResolvedDataSource resolve(String datasourceRef) {
        if (StringUtils.isBlank(datasourceRef)) {
            throw new IllegalArgumentException("datasourceRef must not be blank. Call listDatasources first.");
        }
        if (!StringUtils.startsWith(datasourceRef, REF_PREFIX)) {
            throw new IllegalArgumentException("Invalid datasourceRef format. Call listDatasources again.");
        }

        Map<String, String> fields = parseFields(datasourceRef.substring(REF_PREFIX.length()));
        String signature = fields.remove("sig");
        String payload = canonicalPayload(fields);
        if (!StringUtils.equals(sign(payload), signature)) {
            throw new IllegalArgumentException("Invalid datasourceRef signature. Call listDatasources again.");
        }
        if (Long.parseLong(fields.get("exp")) < Instant.now().getEpochSecond()) {
            throw new IllegalArgumentException("datasourceRef expired. Call listDatasources again.");
        }

        Long dataSourceId = Long.valueOf(fields.get("id"));
        DataSource dataSource = dataSourceService.queryExistent(dataSourceId,
            DataSourceSelector.builder().environment(true).build());
        Map<String, String> currentFields = readableFields(dataSource, Long.parseLong(fields.get("exp")));
        if (!sameDatasource(fields, currentFields)) {
            throw new IllegalArgumentException("Datasource changed after datasourceRef was issued. Call listDatasources again.");
        }
        return ResolvedDataSource.builder()
            .dataSourceId(dataSourceId)
            .dataSource(dataSource)
            .summary(summary(dataSource))
            .build();
    }

    public String summary(DataSource dataSource) {
        return String.format(Locale.ROOT, "id=%s;name=%s;env=%s",
            StringUtils.defaultString(value(dataSource.getId())),
            StringUtils.defaultIfBlank(dataSource.getAlias(), String.valueOf(dataSource.getId())),
            environmentText(dataSource));
    }

    private String canonicalPayload(DataSource dataSource, long expiresAt) {
        return canonicalPayload(readableFields(dataSource, expiresAt));
    }

    private String canonicalPayload(Map<String, String> fields) {
        return fields.entrySet().stream()
            .map(entry -> entry.getKey() + "=" + encode(entry.getValue()))
            .collect(Collectors.joining(";"));
    }

    private Map<String, String> readableFields(DataSource dataSource, long expiresAt) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("id", value(dataSource.getId()));
        fields.put("name", value(dataSource.getAlias()));
        fields.put("env", environmentText(dataSource));
        fields.put("exp", String.valueOf(expiresAt));
        return fields;
    }

    private boolean sameDatasource(Map<String, String> expected, Map<String, String> actual) {
        return StringUtils.equals(expected.get("id"), actual.get("id"))
            && StringUtils.equals(expected.get("name"), actual.get("name"))
            && StringUtils.equals(expected.get("env"), actual.get("env"));
    }

    private String environmentText(DataSource dataSource) {
        if (dataSource.getEnvironment() != null) {
            return StringUtils.defaultIfBlank(dataSource.getEnvironment().getShortName(),
                StringUtils.defaultString(dataSource.getEnvironment().getName()));
        }
        return StringUtils.defaultString(dataSource.getEnvType());
    }

    private Map<String, String> parseFields(String refBody) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (String part : StringUtils.splitPreserveAllTokens(refBody, ';')) {
            int index = StringUtils.indexOf(part, '=');
            if (index <= 0) {
                throw new IllegalArgumentException("Invalid datasourceRef field. Call listDatasources again.");
            }
            fields.put(part.substring(0, index), decode(part.substring(index + 1)));
        }
        return fields;
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(SIGN_ALGORITHM);
            mac.init(new SecretKeySpec(secret, SIGN_ALGORITHM));
            return base64Url(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign datasourceRef.", e);
        }
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String encode(String value) {
        return URLEncoder.encode(value(value), StandardCharsets.UTF_8);
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String value(Object value) {
        return StringUtils.defaultString(value == null ? null : value.toString());
    }
}
