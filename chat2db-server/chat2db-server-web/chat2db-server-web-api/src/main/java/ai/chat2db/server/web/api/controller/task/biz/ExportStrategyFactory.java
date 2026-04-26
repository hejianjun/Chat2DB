package ai.chat2db.server.web.api.controller.task.biz;

import ai.chat2db.server.tools.base.excption.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ExportStrategyFactory {

    @Autowired
    private List<ExportStrategy> exportStrategies;

    public ExportStrategy getStrategy(String exportType) {
        for (ExportStrategy strategy : exportStrategies) {
            if (strategy.supports(exportType)) {
                return strategy;
            }
        }
        throw new BusinessException("dataSource.exportTypeNotSupported", new Object[]{exportType});
    }
}
