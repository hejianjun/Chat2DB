package ai.chat2db.server.domain.api.param;

import lombok.Data;

@Data
public class ColumnConfigParam {

    private String columnName;

    private String dataType;

    private String expression;

    private String comment;

    private Boolean nullable;

    private Integer maxLength;

    private Integer scale;
}
