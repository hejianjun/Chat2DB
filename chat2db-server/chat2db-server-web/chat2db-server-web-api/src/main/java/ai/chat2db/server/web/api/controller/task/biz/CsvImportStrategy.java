package ai.chat2db.server.web.api.controller.task.biz;

import org.springframework.stereotype.Component;

@Component
public class CsvImportStrategy extends AbstractImportStrategy {

    @Override
    public boolean supports(String fileType) {
        return "CSV".equalsIgnoreCase(fileType);
    }
}
