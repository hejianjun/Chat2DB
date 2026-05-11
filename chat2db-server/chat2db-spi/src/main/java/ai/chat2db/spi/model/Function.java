
package ai.chat2db.spi.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * @author jipengfei
 * @version : Function.java
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Function implements IndexModel {
    //FUNCTION_CAT String => function catalog (may be null)
    //FUNCTION_SCHEM String => function schema (may be null)
    //FUNCTION_NAME String => function name. This is the name used to invoke the function
    //REMARKS String => explanatory comment on the function
    //FUNCTION_TYPE short => kind of function:
    //functionResultUnknown - Cannot determine if a return value or table will be returned
    //functionNoTable- Does not return a table
    //functionReturnsTable - Returns a table
    //SPECIFIC_NAME String => the name which uniquely identifies this function within its schema. This is a user specified, or DBMS generated, name that may be different then the FUNCTION_NAME for example with overload functions
    //

    @JsonAlias({"FUNCTION_CAT"})
    @LuceneField(name = "databaseName", type = LuceneFieldType.STRING)
    private String databaseName;

    @JsonAlias({"FUNCTION_SCHEM"})
    @LuceneField(name = "schemaName", type = LuceneFieldType.STRING)
    private String schemaName;

    @JsonAlias({"FUNCTION_NAME"})
    @LuceneField(name = "name", type = LuceneFieldType.TEXT, sort = true)
    private String name;

    @JsonAlias({"REMARKS"})
    @LuceneField(name = "comment", type = LuceneFieldType.TEXT)
    private String comment;

    @LuceneField(name = "aiComment", type = LuceneFieldType.TEXT)
    private String aiComment;

    private Long version;

    @JsonAlias({"FUNCTION_TYPE"})
    private Short functionType;

    @JsonAlias({"SPECIFIC_NAME"})
    private String specificName;

    private String functionBody;

    @Override
    public String getTableName() {
        return null;
    }

    @Override
    public void setTableName(String tableName) {
    }
}