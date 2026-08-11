package ai.chat2db.server.web.api.controller.task;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.ContentDisposition;

class TaskControllerTest {

    @Test
    void hidesInternalTempSuffixFromDownloadFileName() {
        Assertions.assertEquals(
                "查询M2车型数据（排除M2L02）_20260807140309.csv",
                TaskController.resolveDownloadFileName(
                        "查询M2车型数据（排除M2L02）_20260807140309_chat2db_tmp_123456789.csv"));
        Assertions.assertEquals("legacy.csv", TaskController.resolveDownloadFileName("legacy.csv"));
        Assertions.assertEquals(
                "legacy_20260807140309123456789.csv",
                TaskController.resolveDownloadFileName("legacy_20260807140309123456789.csv"));
    }

    @Test
    void encodesUnicodeDownloadFileNameAsUtf8() {
        String filename = "查询M2车型数据（排除M2L02）_20260807140309.csv";
        String header = TaskController.buildContentDisposition(filename);

        Assertions.assertEquals(filename, ContentDisposition.parse(header).getFilename());
    }
}
