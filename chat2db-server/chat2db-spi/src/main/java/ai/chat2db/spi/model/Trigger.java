
package ai.chat2db.spi.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * @author jipengfei
 * @version : Trigger.java
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Trigger implements IndexModel {

    private String databaseName;

    private String schemaName;

    private String name;

    private String comment;

    private String aiComment;

    private Long version;

    private String eventManipulation;

    private String triggerBody;

    @Override
    public String getTableName() {
        return null;
    }

    @Override
    public void setTableName(String tableName) {
    }
}