package ai.chat2db.server.domain.api.service;

import ai.chat2db.server.domain.api.model.schemaDiff.SchemaDiffResult;
import ai.chat2db.server.domain.api.param.schemaDiff.SchemaCompareParam;
import ai.chat2db.server.domain.api.param.schemaDiff.SchemaMigrateParam;
import ai.chat2db.server.domain.api.param.schemaDiff.MigrateResult;

/**
 * 表结构对比与迁移服务。
 *
 * <p>{@link #compare(SchemaCompareParam)} 用于比较源 / 目标库下的表结构（含列、索引、
 * 外键、表选项），并生成可执行的 DDL；{@link #migrate(SchemaMigrateParam)} 用于在
 * 目标库实际执行 DDL 完成迁移。两者通常先后调用：先 compare 拿到差异预览，再让
 * 用户挑选需要执行的语句调用 migrate。
 */
public interface SchemaDiffService {

    /**
     * 比较源 / 目标的表结构。
     *
     * @param param 比较参数（源/目标连接、库/Schema、过滤表名、比较选项）
     * @return 包含差异详情、汇总统计与 DDL 预览的结果
     */
    SchemaDiffResult compare(SchemaCompareParam param);

    /**
     * 在目标库执行 DDL 语句完成迁移，返回每条语句的执行结果。
     */
    MigrateResult migrate(SchemaMigrateParam param);
}
