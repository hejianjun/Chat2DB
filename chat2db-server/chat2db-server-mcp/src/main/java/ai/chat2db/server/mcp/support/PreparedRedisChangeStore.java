package ai.chat2db.server.mcp.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/** Stores validated Redis changes between prepare and execute. */
@Component
public class PreparedRedisChangeStore {

    private static final long OPERATION_TTL_SECONDS = 10 * 60;
    private final ConcurrentMap<String, PreparedRedisChange> changes = new ConcurrentHashMap<>();

    public PreparedRedisChange prepare(PreparedRedisChange input) {
        cleanupExpired();
        String operationId = UUID.randomUUID().toString();
        String digest = digest(input.canonicalInput());
        String confirmationText = "EXECUTE " + input.getTarget() + " sha256:" + digest;
        input.setOperationId(operationId);
        input.setDigest(digest);
        input.setConfirmationText(confirmationText);
        input.setExpiresAt(Instant.now().plusSeconds(OPERATION_TTL_SECONDS).getEpochSecond());
        changes.put(operationId, input);
        return input;
    }

    public PreparedRedisChange consume(String operationId, String confirmationText) {
        cleanupExpired();
        PreparedRedisChange change = changes.remove(operationId);
        if (change == null) {
            throw new IllegalArgumentException("Prepared Redis change not found or expired.");
        }
        if (!StringUtils.equals(change.getConfirmationText(), confirmationText)) {
            throw new IllegalArgumentException("Confirmation text does not match the prepared Redis change.");
        }
        return change;
    }

    private void cleanupExpired() {
        long now = Instant.now().getEpochSecond();
        changes.entrySet().removeIf(entry -> entry.getValue().getExpiresAt() < now);
    }

    private String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                builder.append(String.format("%02x", bytes[i]));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to digest prepared Redis change.", e);
        }
    }

    @Data
    @Builder
    public static class PreparedRedisChange {
        private String operationId;
        private String operation;
        private String datasourceUri;
        private String databaseName;
        private String key;
        private String newKey;
        private String keyType;
        private Object value;
        private Map<String, String> hashUpserts;
        private List<String> hashDeletes;
        private List<String> setAdds;
        private List<String> setRemoves;
        private Map<String, Double> zsetUpserts;
        private List<String> zsetRemoves;
        private Long ttlSeconds;
        private String fingerprint;
        private String target;
        private String digest;
        private String confirmationText;
        private long expiresAt;

        public String canonicalInput() {
            return String.join("|", StringUtils.defaultString(operation), StringUtils.defaultString(datasourceUri),
                    StringUtils.defaultString(databaseName), StringUtils.defaultString(key),
                    StringUtils.defaultString(newKey), StringUtils.defaultString(keyType), String.valueOf(value),
                    String.valueOf(hashUpserts), String.valueOf(hashDeletes), String.valueOf(setAdds),
                    String.valueOf(setRemoves), String.valueOf(zsetUpserts), String.valueOf(zsetRemoves),
                    String.valueOf(ttlSeconds), StringUtils.defaultString(fingerprint));
        }
    }
}
