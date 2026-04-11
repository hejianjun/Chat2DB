package ai.chat2db.server.web.api.controller.rdb.request;

import java.io.Serial;

import jakarta.validation.constraints.NotNull;

import ai.chat2db.server.tools.base.wrapper.request.PageQueryRequest;
import ai.chat2db.server.web.api.controller.data.source.request.DataSourceBaseRequestInfo;

import lombok.Data;

@Data
public class TreeSearchRequest extends PageQueryRequest implements DataSourceBaseRequestInfo {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull
    private Long dataSourceId;

    private String databaseName;

    private String schemaName;

    private String searchKey;

    private String treeNodeType;

    private boolean refresh;
}