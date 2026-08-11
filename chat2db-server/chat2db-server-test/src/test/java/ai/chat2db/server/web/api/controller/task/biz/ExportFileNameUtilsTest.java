package ai.chat2db.server.web.api.controller.task.biz;

import cn.hutool.core.io.FileUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.LocalDateTime;

class ExportFileNameUtilsTest {

    @Test
    void usesConsoleTitleAndPreservesUnicode() {
        String title = "查询M2车型数据（排除M2L02）";

        Assertions.assertEquals(title, ExportFileNameUtils.resolveBaseName(title, "vehicle_model_matlab"));
        Assertions.assertEquals(
                title + "_20260807140309_chat2db_tmp_",
                ExportFileNameUtils.buildTempFilePrefix(title, LocalDateTime.of(2026, 8, 7, 14, 3, 9)));
    }

    @Test
    void fallsBackToTableNameAndSanitizesWindowsFileNames() {
        Assertions.assertEquals(
                "vehicle_model_matlab",
                ExportFileNameUtils.resolveBaseName(" ", "vehicle_model_matlab"));
        Assertions.assertEquals(
                "车型_查询_结果",
                ExportFileNameUtils.resolveBaseName("车型/查询:结果. ", "table1"));
        Assertions.assertEquals("_CON", ExportFileNameUtils.resolveBaseName("CON", "table1"));
    }

    @Test
    void createsReadableUnicodeTempFileName() {
        String title = "查询M2车型数据（排除M2L02）";
        String prefix = ExportFileNameUtils.buildTempFilePrefix(
                title, LocalDateTime.of(2026, 8, 7, 14, 3, 9));
        File file = FileUtil.createTempFile(prefix, ".csv", true);

        try {
            Assertions.assertTrue(file.getName().startsWith(title + "_20260807140309_chat2db_tmp_"));
            Assertions.assertTrue(file.getName().endsWith(".csv"));
            Assertions.assertTrue(file.canRead());
        } finally {
            FileUtil.del(file);
        }
    }
}
