package ai.chat2db.server.web.api.controller.task.biz.doc;

import ai.chat2db.server.tools.base.excption.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SchemaDocExportStrategyFactory {

    @Autowired
    private List<SchemaDocExportStrategy> strategies;

    public SchemaDocExportStrategy getStrategy(String exportType) {
        for (SchemaDocExportStrategy strategy : strategies) {
            if (strategy.supports(exportType)) {
                return strategy;
            }
        }
        throw new BusinessException("dataSource.exportTypeNotSupported", new Object[]{exportType});
    }
}
