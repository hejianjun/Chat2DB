package ai.chat2db.server.web.api.controller.rdb;

import ai.chat2db.server.domain.api.param.DataGenerationConfigQueryParam;
import ai.chat2db.server.domain.api.param.DataGenerationConfigSaveParam;
import ai.chat2db.server.domain.api.service.DataGenerationConfigService;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.web.api.aspect.ConnectionInfoAspect;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@ConnectionInfoAspect
@RequestMapping("/api/rdb/table/generation-config")
public class DataGenerationConfigController {

    @Autowired
    private DataGenerationConfigService configService;

    @PostMapping("/save")
    public ActionResult saveConfig(@RequestBody DataGenerationConfigSaveParam param) {
        try {
            return configService.saveConfig(param);
        } catch (Exception e) {
            log.error("Failed to save data generation config", e);
            return ActionResult.fail("SAVE_CONFIG_ERROR", "保存配置失败: " + e.getMessage(), null);
        }
    }

    @GetMapping("/get")
    public DataResult<DataGenerationConfigQueryParam> getConfigByTable(
            @RequestParam Long dataSourceId,
            @RequestParam String databaseName,
            @RequestParam(required = false) String schemaName,
            @RequestParam String tableName) {
        try {
            return configService.getConfigByTable(dataSourceId, databaseName, schemaName, tableName);
        } catch (Exception e) {
            log.error("Failed to get data generation config by table", e);
            return DataResult.error("GET_CONFIG_ERROR", "获取配置失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ActionResult deleteConfig(@PathVariable Long id) {
        try {
            return configService.deleteConfig(id);
        } catch (Exception e) {
            log.error("Failed to delete data generation config", e);
            return ActionResult.fail("DELETE_CONFIG_ERROR", "删除配置失败: " + e.getMessage(), null);
        }
    }
}
