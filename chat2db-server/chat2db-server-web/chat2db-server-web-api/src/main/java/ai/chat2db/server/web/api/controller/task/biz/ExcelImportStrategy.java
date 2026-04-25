package ai.chat2db.server.web.api.controller.task.biz;

import org.springframework.stereotype.Component;

@Component
public class ExcelImportStrategy extends AbstractImportStrategy {

    @Override
    public boolean supports(String fileType) {
        return "XLSX".equalsIgnoreCase(fileType) || "XLS".equalsIgnoreCase(fileType);
    }
}
