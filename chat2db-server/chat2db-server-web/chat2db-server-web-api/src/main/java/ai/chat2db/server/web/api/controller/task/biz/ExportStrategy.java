package ai.chat2db.server.web.api.controller.task.biz;

public interface ExportStrategy {

    void exportData(ExportContext exportContext);

    boolean supports(String exportType);
}
