package ai.chat2db.spi.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 虚拟外键推断建议
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VirtualForeignKeySuggestion {

    /**
     * 来源表名
     */
    private String sourceTable;

    /**
     * 来源列名
     */
    private String sourceColumn;

    /**
     * 目标表名
     */
    private String targetTable;

    /**
     * 目标列名
     */
    private String targetColumn;

    /**
     * 推断原因 (e.g., "JOIN condition", "WHERE clause")
     */
    private String reason;
}
