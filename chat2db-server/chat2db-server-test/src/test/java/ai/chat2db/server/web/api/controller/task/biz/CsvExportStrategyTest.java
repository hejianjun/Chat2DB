package ai.chat2db.server.web.api.controller.task.biz;

import ai.chat2db.spi.model.ExecuteResult;
import ai.chat2db.spi.model.Header;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

class CsvExportStrategyTest {

    @Test
    void writesMultipleCommandPagesAndPreservesRawValues() throws IOException {
        Path file = Files.createTempFile("chat2db-command-export-", ".csv");
        List<Integer> progress = new ArrayList<>();
        ExecuteResult first = result(
                List.of("id", "startedAt"),
                List.of(List.of("first", "2026-07-23T07:40:11.753Z")));
        ExecuteResult second = result(
                List.of("startedAt", "id"),
                List.of(List.of("2026-07-23T08:40:11.753Z", "second")));
        ExportContext context = ExportContext.builder()
                .file(file.toFile())
                .commandResults(List.of(first, second))
                .progressUpdater(progress::add)
                .build();

        try {
            new CsvExportStrategy().exportData(context);

            String csv = Files.readString(file);
            Assertions.assertTrue(csv.contains("id,startedAt"));
            Assertions.assertTrue(csv.contains("first,2026-07-23T07:40:11.753Z"));
            Assertions.assertTrue(csv.contains("second,2026-07-23T08:40:11.753Z"));
            Assertions.assertEquals(2, progress.get(progress.size() - 1));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static ExecuteResult result(List<String> headerNames, List<List<String>> rows) {
        List<Header> headers = new ArrayList<>();
        headerNames.forEach(name -> headers.add(Header.builder().name(name).build()));
        return ExecuteResult.builder()
                .success(true)
                .headerList(headers)
                .dataList(rows)
                .build();
    }
}
