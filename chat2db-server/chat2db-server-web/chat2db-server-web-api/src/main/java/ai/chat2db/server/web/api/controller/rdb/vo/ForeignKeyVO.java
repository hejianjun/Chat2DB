package ai.chat2db.server.web.api.controller.rdb.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ForeignKeyVO {

    private Long id;

    private String name;

    private String tableName;

    private String columnName;

    private String referencedTable;

    private String referencedColumnName;

    private String comment;

    private Integer updateRule;

    private Integer deleteRule;

    private String sourceType;

    private Boolean editable;

    private String virtualProperty;

    private LocalDateTime syncTime;
}
