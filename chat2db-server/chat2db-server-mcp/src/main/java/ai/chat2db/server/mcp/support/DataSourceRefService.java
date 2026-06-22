package ai.chat2db.server.mcp.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import ai.chat2db.server.domain.api.model.DataSource;
import ai.chat2db.server.domain.api.param.datasource.DataSourceSelector;
import ai.chat2db.server.domain.api.service.DataSourceService;
import ai.chat2db.server.tools.common.model.LoginUser;
import ai.chat2db.server.tools.common.util.ContextUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Issues and verifies signed datasource references for MCP tools.
 */
@Component
public class DataSourceRefService {

    private static final String SIGN_ALGORITHM = "HmacSHA256";
    private static final String DIGEST_ALGORITHM = "SHA-256";
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
        String payload = String.join("|",
            String.valueOf(dataSource.getId()),
            currentUserKey(),
            String.valueOf(expiresAt),
            fingerprint(dataSource));
        String encodedPayload = base64Url(payload.getBytes(StandardCharsets.UTF_8));
        return encodedPayload + "." + sign(encodedPayload);
    }

    public ResolvedDataSource resolve(String datasourceRef) {
        if (StringUtils.isBlank(datasourceRef)) {
            throw new IllegalArgumentException("datasourceRef must not be blank. Call listDatasources first.");
        }
        String[] parts = datasourceRef.split("\\.", 2);
        if (parts.length != 2 || !StringUtils.equals(sign(parts[0]), parts[1])) {
            throw new IllegalArgumentException("Invalid datasourceRef signature. Call listDatasources again.");
        }

        String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        String[] fields = payload.split("\\|", -1);
        if (fields.length != 4) {
            throw new IllegalArgumentException("Invalid datasourceRef payload. Call listDatasources again.");
        }
        if (!StringUtils.equals(fields[1], currentUserKey())) {
            throw new IllegalArgumentException("datasourceRef belongs to another user/session. Call listDatasources again.");
        }
        if (Long.parseLong(fields[2]) < Instant.now().getEpochSecond()) {
            throw new IllegalArgumentException("datasourceRef expired. Call listDatasources again.");
        }

        Long dataSourceId = Long.valueOf(fields[0]);
        DataSource dataSource = dataSourceService.queryExistent(dataSourceId,
            DataSourceSelector.builder().environment(true).build());
        String currentFingerprint = fingerprint(dataSource);
        if (!StringUtils.equals(fields[3], currentFingerprint)) {
            throw new IllegalArgumentException("Datasource changed after datasourceRef was issued. Call listDatasources again.");
        }
        return ResolvedDataSource.builder()
            .dataSourceId(dataSourceId)
            .dataSource(dataSource)
            .fingerprint(currentFingerprint)
            .summary(summary(dataSource))
            .build();
    }

    public String fingerprint(DataSource dataSource) {
        String text = String.join("|",
            value(dataSource.getId()),
            value(dataSource.getAlias()),
            value(dataSource.getType()),
            value(dataSource.getHost()),
            value(dataSource.getPort()),
            value(dataSource.getUrl()),
            value(dataSource.getEnvType()),
            value(dataSource.getEnvironmentId()),
            dataSource.getEnvironment() == null ? "" : value(dataSource.getEnvironment().getShortName()),
            dataSource.getEnvironment() == null ? "" : value(dataSource.getEnvironment().getName()));
        return sha256(text);
    }

    public String summary(DataSource dataSource) {
        String env = dataSource.getEnvironment() == null ? dataSource.getEnvType() : dataSource.getEnvironment().getShortName();
        return String.format(Locale.ROOT, "%s[%s]@%s:%s env=%s",
            StringUtils.defaultIfBlank(dataSource.getAlias(), String.valueOf(dataSource.getId())),
            StringUtils.defaultString(dataSource.getType()),
            StringUtils.defaultString(dataSource.getHost()),
            StringUtils.defaultString(dataSource.getPort()),
            StringUtils.defaultString(env));
    }

    private String currentUserKey() {
        LoginUser loginUser = ContextUtils.queryLoginUser();
        return loginUser == null || loginUser.getId() == null ? "anonymous" : loginUser.getId().toString();
    }

    private String sign(String encodedPayload) {
        try {
            Mac mac = Mac.getInstance(SIGN_ALGORITHM);
            mac.init(new SecretKeySpec(secret, SIGN_ALGORITHM));
            return base64Url(mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign datasourceRef.", e);
        }
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
            return base64Url(digest.digest(text.getBytes(StandardCharsets.UTF_8))).substring(0, 16);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build datasource fingerprint.", e);
        }
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String value(Object value) {
        return StringUtils.defaultString(value == null ? null : value.toString());
    }
}
