package ai.chat2db.server.web.api.controller.ai.statemachine.actions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

class SaveAiCommentActionTest {

    @Test
    void normalizeBatchTableNameRemovesDatabaseQualifier() {
        String tableName = SaveAiCommentAction.normalizeBatchTableName(
                "vehicle_data_uat.recording_data_1",
                List.of("recording_data_1", "triggered_data_20"),
                "vehicle_data_uat",
                null);

        assertEquals("recording_data_1", tableName);
    }

    @Test
    void normalizeBatchTableNameRemovesQuotedDatabaseQualifier() {
        String tableName = SaveAiCommentAction.normalizeBatchTableName(
                "`vehicle_data_uat`.`recording_data_1`",
                List.of("recording_data_1"),
                "vehicle_data_uat",
                null);

        assertEquals("recording_data_1", tableName);
    }

    @Test
    void normalizeBatchTableNameSkipsUnknownTable() {
        String tableName = SaveAiCommentAction.normalizeBatchTableName(
                "vehicle_data_uat.unknown_table",
                List.of("recording_data_1"),
                "vehicle_data_uat",
                null);

        assertNull(tableName);
    }

    @Test
    void extractCompleteBatchTableObjectsFromTruncatedResponse() {
        String content = "```json\n{\"tables\":["
                + "{\"table_name\":\"first\",\"table_comment\":\"first comment\"},"
                + "{\"table_name\":\"second\",\"table_comment\":\"包含 } 和转义引号 \\\" 的注释\"},"
                + "{\"table_name\":\"third\",\"table_comment";

        List<String> objects = SaveAiCommentAction.extractCompleteBatchTableObjects(content);

        assertEquals(2, objects.size());
        assertEquals("{\"table_name\":\"first\",\"table_comment\":\"first comment\"}", objects.get(0));
        assertEquals("{\"table_name\":\"second\",\"table_comment\":\"包含 } 和转义引号 \\\" 的注释\"}",
                objects.get(1));
    }
}
