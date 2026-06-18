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
}
