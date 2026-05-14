package ai.chat2db.server.domain.api.param;

import lombok.Data;

import java.util.Map;

@Data
public class ColumnConfigParam {

    private String columnName;

    private String dataType;

    private String generationType;

    private String subType;

    private String comment;

    private Boolean nullable;

    private Integer maxLength;

    private Integer scale;

    private Map<String, Object> customParams;
}
