package ai.chat2db.server.mcp.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import ai.chat2db.server.tools.common.model.LoginUser;
import ai.chat2db.server.tools.common.util.ContextUtils;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Single-use prepared MongoDB changes, isolated from the existing SQL confirmation store.
 */
@Component
public class PreparedMongoChangeStore {

    private static final long OPERATION_TTL_SECONDS = 10 * 60;
    private static final String MCP_SESSION_HEADER = "Mcp-Session-Id";

    private final ConcurrentMap<String, PreparedMongoChange> changes = new ConcurrentHashMap<>();
    private final Clock clock;

    public PreparedMongoChangeStore() {
        this(Clock.systemUTC());
    }

    PreparedMongoChangeStore(Clock clock) {
        this.clock = clock;
    }

    public PreparedMongoChange prepare(String category, String operation, String datasourceUri,
                                       ResolvedDataSource dataSource, String databaseName, String collection,
                                       String mql, String countMql, Long estimatedMatchedDocuments,
                                       Integer maxAffectedDocuments, String riskLevel) {
        cleanupExpired();
        String operationId = UUID.randomUUID().toString();
        String target = target(dataSource, databaseName, collection);
        String digest = digest(String.join("\n", category, operation, target, mql,
            StringUtils.defaultString(countMql), String.valueOf(maxAffectedDocuments)));
        String confirmationText = "EXECUTE MONGODB " + operation + " " + target + " sha256:" + digest;
        PreparedMongoChange change = PreparedMongoChange.builder()
            .operationId(operationId)
            .category(category)
            .operation(operation)
            .datasourceUri(datasourceUri)
            .databaseName(databaseName)
            .collection(collection)
            .mql(mql)
            .countMql(countMql)
            .estimatedMatchedDocuments(estimatedMatchedDocuments)
            .maxAffectedDocuments(maxAffectedDocuments)
            .riskLevel(riskLevel)
            .target(target)
            .digest(digest)
            .confirmationText(confirmationText)
            .userKey(currentUserKey())
            .expiresAt(Instant.now(clock).plusSeconds(OPERATION_TTL_SECONDS).getEpochSecond())
            .build();
        changes.put(operationId, change);
        return change;
    }

    public PreparedMongoChange consume(String operationId, String confirmationText) {
        cleanupExpired();
        PreparedMongoChange change = changes.remove(operationId);
        if (change == null) {
            throw new IllegalArgumentException(
                "Prepared MongoDB change not found or expired. Call prepareMongoWriteChange or "
                    + "prepareMongoAdminChange again.");
        }
        if (StringUtils.isNotBlank(change.getUserKey()) && !StringUtils.equals(change.getUserKey(), currentUserKey())) {
            throw new IllegalArgumentException("Prepared MongoDB change belongs to another user/session.");
        }
        if (!StringUtils.equals(change.getConfirmationText(), confirmationText)) {
            throw new IllegalArgumentException("Confirmation text does not match the prepared MongoDB change.");
        }
        return change;
    }

    private void cleanupExpired() {
        long now = Instant.now(clock).getEpochSecond();
        changes.entrySet().removeIf(entry -> entry.getValue().getExpiresAt() < now);
    }

    private String target(ResolvedDataSource dataSource, String databaseName, String collection) {
        return dataSource.getSummary() + "/" + StringUtils.defaultString(databaseName)
            + (StringUtils.isBlank(collection) ? "" : "." + collection);
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
            throw new IllegalStateException("Failed to digest prepared MongoDB change.", e);
        }
    }

    private String currentUserKey() {
        LoginUser loginUser = ContextUtils.queryLoginUser();
        String userId = loginUser == null || loginUser.getId() == null ? null : loginUser.getId().toString();
        String sessionId = null;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            sessionId = attributes.getRequest().getHeader(MCP_SESSION_HEADER);
        }
        if (StringUtils.isAllBlank(userId, sessionId)) {
            return null;
        }
        return "user:" + StringUtils.defaultString(userId) + "|session:" + StringUtils.defaultString(sessionId);
    }

    @Data
    @Builder
    public static class PreparedMongoChange {
        private String operationId;
        private String category;
        private String operation;
        private String datasourceUri;
        private String databaseName;
        private String collection;
        private String mql;
        private String countMql;
        private Long estimatedMatchedDocuments;
        private Integer maxAffectedDocuments;
        private String riskLevel;
        private String target;
        private String digest;
        private String confirmationText;
        private String userKey;
        private long expiresAt;
    }
}
