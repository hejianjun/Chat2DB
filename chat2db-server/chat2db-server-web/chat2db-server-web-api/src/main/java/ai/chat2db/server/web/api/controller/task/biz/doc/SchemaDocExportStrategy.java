package ai.chat2db.server.web.api.controller.task.biz.doc;

public interface SchemaDocExportStrategy {

    void export(SchemaDocExportContext context);

    boolean supports(String exportType);
}
