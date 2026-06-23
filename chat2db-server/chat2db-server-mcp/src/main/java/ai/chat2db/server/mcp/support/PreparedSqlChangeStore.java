package ai.chat2db.server.mcp.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import ai.chat2db.server.tools.common.model.LoginUser;
import ai.chat2db.server.tools.common.util.ContextUtils;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Stores dangerous SQL operations after prepare and before explicit execution.
 */
@Component
public class PreparedSqlChangeStore {

    private static final long OPERATION_TTL_SECONDS = 10 * 60;

    private final ConcurrentMap<String, PreparedSqlChange> changes = new ConcurrentHashMap<>();

    public PreparedSqlChange prepare(String operationType, String datasourceRef, ResolvedDataSource dataSource,
        String databaseName, String schemaName, List<String> sqlList, List<Integer> expectedUpdateCounts) {
        cleanupExpired();
        String operationId = UUID.randomUUID().toString();
        String sqlDigest = digest(String.join("\n", sqlList));
        String target = target(dataSource, databaseName, schemaName);
        String confirmationText = "EXECUTE " + target + " sha256:" + sqlDigest;
        PreparedSqlChange change = PreparedSqlChange.builder()
            .operationId(operationId)
            .operationType(operationType)
            .datasourceRef(datasourceRef)
            .dataSource(dataSource)
            .databaseName(databaseName)
            .schemaName(schemaName)
            .sqlList(List.copyOf(sqlList))
            .expectedUpdateCounts(expectedUpdateCounts == null ? null : List.copyOf(expectedUpdateCounts))
            .confirmationText(confirmationText)
            .sqlDigest(sqlDigest)
            .target(target)
            .userKey(currentUserKey())
            .expiresAt(Instant.now().plusSeconds(OPERATION_TTL_SECONDS).getEpochSecond())
            .build();
        changes.put(operationId, change);
        return change;
    }

    public PreparedSqlChange consume(String operationId, String confirmationText) {
        cleanupExpired();
        PreparedSqlChange change = changes.remove(operationId);
        if (change == null) {
            throw new IllegalArgumentException("Prepared SQL change not found or expired. Call prepareUpdateSqlChange or prepareDdlSqlChange again.");
        }
        if (StringUtils.isNotBlank(change.getUserKey()) && !StringUtils.equals(change.getUserKey(), currentUserKey())) {
            throw new IllegalArgumentException("Prepared SQL change belongs to another user/session.");
        }
        if (!StringUtils.equals(change.getConfirmationText(), confirmationText)) {
            throw new IllegalArgumentException("Confirmation text does not match the prepared SQL change.");
        }
        return change;
    }

    private void cleanupExpired() {
        long now = Instant.now().getEpochSecond();
        changes.entrySet().removeIf(entry -> entry.getValue().getExpiresAt() < now);
    }

    private String target(ResolvedDataSource dataSource, String databaseName, String schemaName) {
        return dataSource.getSummary() + "/" + StringUtils.defaultString(databaseName)
            + (StringUtils.isBlank(schemaName) ? "" : "." + schemaName);
    }

    private String digest(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                builder.append(String.format("%02x", bytes[i]));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to digest prepared SQL.", e);
        }
    }

    private String currentUserKey() {
        LoginUser loginUser = ContextUtils.queryLoginUser();
        return loginUser == null || loginUser.getId() == null ? null : loginUser.getId().toString();
    }

    @Data
    @Builder
    public static class PreparedSqlChange {

        private String operationId;

        private String operationType;

        private String datasourceRef;

        private ResolvedDataSource dataSource;

        private String databaseName;

        private String schemaName;

        private List<String> sqlList;

        private List<Integer> expectedUpdateCounts;

        private String confirmationText;

        private String sqlDigest;

        private String target;

        private String userKey;

        private long expiresAt;
    }
}
