package ai.chat2db.server.domain.api.param;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CreateVirtualFKParam {

    @NotNull
    private Long dataSourceId;

    private String databaseName;

    private String schemaName;

    @NotBlank
    private String tableName;

    @NotBlank
    private String columnName;

    @NotBlank
    private String referencedTable;

    @NotBlank
    private String referencedColumnName;

    private String comment;

    /**
     * 来源类型: MANUAL (手动创建) | INFERRED (推断生成)
     */
    @Builder.Default
    private String sourceType = "MANUAL";
}
