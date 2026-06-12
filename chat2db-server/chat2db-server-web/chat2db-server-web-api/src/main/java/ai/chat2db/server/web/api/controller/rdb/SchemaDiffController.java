package ai.chat2db.server.web.api.controller.rdb;

import ai.chat2db.server.domain.api.model.schemaDiff.SchemaDiffResult;
import ai.chat2db.server.domain.api.param.schemaDiff.MigrateResult;
import ai.chat2db.server.domain.api.param.schemaDiff.SchemaCompareParam;
import ai.chat2db.server.domain.api.param.schemaDiff.SchemaMigrateParam;
import ai.chat2db.server.domain.api.service.SchemaDiffService;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.web.api.controller.rdb.request.SchemaCompareRequest;
import ai.chat2db.server.web.api.controller.rdb.request.SchemaMigrateRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 表结构对比与迁移接口。
 *
 * <p>提供两个端点：
 * <ul>
 *   <li>{@code POST /api/rdb/schema/diff/compare}：对比两个数据源 + 库 + Schema 的表结构差异；</li>
 *   <li>{@code POST /api/rdb/schema/diff/migrate}：在目标数据源执行选中的 DDL 语句。</li>
 * </ul>
 *
 * <p>真正的对比与执行逻辑在 {@link SchemaDiffService} 实现中，本控制器只负责
 * 把请求参数转换为领域参数对象并包装统一响应。
 */
@RestController
@RequestMapping("/api/rdb/schema/diff")
public class SchemaDiffController {

    @Autowired
    private SchemaDiffService schemaDiffService;

    /**
     * 比较源/目标的表结构。
     *
     * <p>{@link SchemaCompareRequest#getCompareOption()} 为空时使用默认 {@link ai.chat2db.server.domain.api.param.schemaDiff.CompareOption}，
     * 其默认开启列/索引/外键/表选项的全部对比。
     */
    @PostMapping("/compare")
    public DataResult<SchemaDiffResult> compare(@Valid @RequestBody SchemaCompareRequest request) {
        SchemaCompareParam param = SchemaCompareParam.builder()
                .sourceDataSourceId(request.getSourceDataSourceId())
                .sourceDatabaseName(request.getSourceDatabaseName())
                .sourceSchemaName(request.getSourceSchemaName())
                .targetDataSourceId(request.getTargetDataSourceId())
                .targetDatabaseName(request.getTargetDatabaseName())
                .targetSchemaName(request.getTargetSchemaName())
                .tableNames(request.getTableNames())
                .compareOption(request.getCompareOption() != null
                        ? request.getCompareOption()
                        : new ai.chat2db.server.domain.api.param.schemaDiff.CompareOption())
                .build();
        SchemaDiffResult result = schemaDiffService.compare(param);
        return DataResult.of(result);
    }

    /**
     * 在目标库执行用户选中的 DDL 语句完成迁移。
     *
     * <p>支持事务执行与失败继续两种模式，由 {@link SchemaMigrateRequest} 中的开关控制。
     */
    @PostMapping("/migrate")
    public DataResult<MigrateResult> migrate(@Valid @RequestBody SchemaMigrateRequest request) {
        SchemaMigrateParam param = SchemaMigrateParam.builder()
                .targetDataSourceId(request.getTargetDataSourceId())
                .targetDatabaseName(request.getTargetDatabaseName())
                .targetSchemaName(request.getTargetSchemaName())
                .ddlStatements(request.getDdlStatements())
                .executeInTransaction(request.isExecuteInTransaction())
                .continueOnError(request.isContinueOnError())
                .build();
        MigrateResult result = schemaDiffService.migrate(param);
        return DataResult.of(result);
    }
}
