package ai.chat2db.server.web.api.controller.task.request;

import ai.chat2db.server.web.api.controller.data.source.request.DataSourceBaseRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 文件预览请求参数
 */
@Data
public class FilePreviewRequest extends DataSourceBaseRequest {

    /**
     * 目标表名
     */
    @NotBlank
    private String tableName;

    /**
     * 文件类型：CSV, XLSX, XLS
     */
    @NotNull
    private String fileType;

    /**
     * 数据库名
     */
    private String databaseName;

    /**
     * schema 名
     */
    private String schemaName;
}
