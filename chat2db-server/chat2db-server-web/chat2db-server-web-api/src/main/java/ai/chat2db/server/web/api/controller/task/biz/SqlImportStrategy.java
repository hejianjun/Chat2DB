package ai.chat2db.server.web.api.controller.task.biz;

import ai.chat2db.server.tools.base.excption.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class SqlImportStrategy implements ImportStrategy {

    @Override
    public void importData(File file, ImportContext importContext) {
        final AtomicInteger processedCount = new AtomicInteger(0);

        try (Statement statement = importContext.getConnection().createStatement()) {
            List<String> sqlStatements = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);

            for (String sql : sqlStatements) {
                if (StringUtils.isNotBlank(sql) && !sql.trim().startsWith("--")) {
                    statement.executeUpdate(sql);
                    int count = processedCount.incrementAndGet();

                    if (count % 200 == 0) {
                        importContext.getProgressUpdater().accept(count);
                    }
                }
            }
            importContext.getProgressUpdater().accept(processedCount.get());
        } catch (SQLException e) {
            log.error("import sql error", e);
            throw new BusinessException("dataSource.importError");
        } catch (IOException e) {
            log.error("import sql error", e);
            throw new BusinessException("dataSource.importError");
        }
    }

    @Override
    public boolean supports(String fileType) {
        return "SQL".equalsIgnoreCase(fileType);
    }
}
