
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

    @LuceneField(name = "databaseName", type = LuceneFieldType.STRING)
    private String databaseName;

    @LuceneField(name = "schemaName", type = LuceneFieldType.STRING)
    private String schemaName;

    @LuceneField(name = "name", type = LuceneFieldType.TEXT)
    private String name;

    @LuceneField(name = "comment", type = LuceneFieldType.TEXT)
    private String comment;

    @LuceneField(name = "aiComment", type = LuceneFieldType.TEXT)
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