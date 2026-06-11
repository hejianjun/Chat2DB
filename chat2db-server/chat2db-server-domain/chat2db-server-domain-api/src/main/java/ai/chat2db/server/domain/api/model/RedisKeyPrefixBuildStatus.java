package ai.chat2db.server.domain.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedisKeyPrefixBuildStatus {

    private String status;

    private String cursor;

    private Long scannedCount;

    private Long prefixCount;

    private Long refreshVersion;

    private String startedAt;

    private String updatedAt;

    private String errorMessage;
}
