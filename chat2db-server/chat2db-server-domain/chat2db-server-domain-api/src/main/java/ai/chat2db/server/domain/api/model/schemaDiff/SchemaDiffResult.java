package ai.chat2db.server.domain.api.model.schemaDiff;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 表结构对比结果。
 *
 * <p>由 {@link ai.chat2db.server.domain.api.service.SchemaDiffService#compare} 返回，
 * 用于给前端展示差异列表并驱动后续 DDL 迁移流程。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class SchemaDiffResult {
    /** 源连接标识，格式为 {@code dataSourceId.database[.schema]}，前端可据此回填迁移参数。 */
    private String sourceKey;
    /** 目标连接标识，格式同 {@code sourceKey}。 */
    private String targetKey;
    /** 比较结果汇总（总表数、新增/删除/修改/未变化数量、被排除的废弃表数等）。 */
    private DiffSummary summary;
    /** 各表的差异详情；为减小响应体积，结构完全一致的表通常会被过滤掉。 */
    private List<TableDiff> tableDiffs;
    /** 比较过程中产生的告警信息，例如某些表读取失败、不支持的方言特性等。 */
    private List<String> warnings;
}
