package ai.chat2db.server.web.api.controller.task.biz;

import java.io.File;

public interface ImportStrategy {

    void importData(File file, ImportContext context);

    boolean supports(String fileType);
}
