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

    /** 构建状态：IDLE / RUNNING / DONE / FAILED */
    private String status;

    /** 游标，用于继续扫描的进度标识 */
    private String cursor;

    /** 已扫描的 key 数量 */
    private Long scannedCount;

    /** 已生成的前缀数量 */
    private Long prefixCount;

    /** 刷新版本号 */
    private Long refreshVersion;

    /** 开始时间 */
    private String startedAt;

    /** 更新时间 */
    private String updatedAt;

    /** 错误信息 */
    private String errorMessage;
}
