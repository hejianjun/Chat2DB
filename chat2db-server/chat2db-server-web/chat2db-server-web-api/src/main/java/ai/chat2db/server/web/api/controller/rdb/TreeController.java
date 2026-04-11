package ai.chat2db.server.web.api.controller.rdb;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ai.chat2db.server.domain.api.model.TreeNode;
import ai.chat2db.server.domain.api.param.TreeSearchParam;
import ai.chat2db.server.domain.api.service.FunctionService;
import ai.chat2db.server.domain.api.service.ProcedureService;
import ai.chat2db.server.domain.api.service.TableService;
import ai.chat2db.server.domain.api.service.TriggerService;
import ai.chat2db.server.domain.api.service.ViewService;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;
import ai.chat2db.server.web.api.aspect.ConnectionInfoAspect;
import ai.chat2db.server.web.api.controller.rdb.request.TreeSearchRequest;
import ai.chat2db.server.web.api.controller.rdb.vo.TreeNodeVO;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ConnectionInfoAspect
@RequestMapping("/api/rdb/tree")
@RestController
public class TreeController {

    @Autowired
    private TableService tableService;

    @Autowired
    private ViewService viewService;

    @Autowired
    private FunctionService functionService;

    @Autowired
    private ProcedureService procedureService;

    @Autowired
    private TriggerService triggerService;

    @GetMapping("/search")
    public ListResult<TreeNodeVO> search(@Valid TreeSearchRequest request) {
        TreeSearchParam param = toParam(request);
        List<TreeNode> result = new ArrayList<>();

        String type = request.getTreeNodeType();
        if (StringUtils.isBlank(type) || "all".equalsIgnoreCase(type)) {
            result.addAll(tableService.searchTreeNodes(param));
            result.addAll(viewService.searchTreeNodes(param));
            result.addAll(functionService.searchTreeNodes(param));
            result.addAll(procedureService.searchTreeNodes(param));
            result.addAll(triggerService.searchTreeNodes(param));
        } else {
            String lowerType = type.toLowerCase();
            switch (lowerType) {
                case "table":
                    result.addAll(tableService.searchTreeNodes(param));
                    break;
                case "view":
                    result.addAll(viewService.searchTreeNodes(param));
                    break;
                case "function":
                    result.addAll(functionService.searchTreeNodes(param));
                    break;
                case "procedure":
                    result.addAll(procedureService.searchTreeNodes(param));
                    break;
                case "trigger":
                    result.addAll(triggerService.searchTreeNodes(param));
                    break;
                default:
                    log.warn("Unknown tree node type: {}", type);
            }
        }

        return ListResult.of(toVOList(result));
    }

    private TreeSearchParam toParam(TreeSearchRequest request) {
        return TreeSearchParam.builder()
                .dataSourceId(request.getDataSourceId())
                .databaseName(request.getDatabaseName())
                .schemaName(request.getSchemaName())
                .searchKey(request.getSearchKey())
                .treeNodeType(request.getTreeNodeType())
                .refresh(request.isRefresh())
                .build();
    }

    private List<TreeNodeVO> toVOList(List<TreeNode> nodes) {
        List<TreeNodeVO> voList = new ArrayList<>();
        for (TreeNode node : nodes) {
            TreeNodeVO vo = new TreeNodeVO();
            vo.setUuid(node.getUuid());
            vo.setKey(node.getKey());
            vo.setName(node.getName());
            vo.setTreeNodeType(node.getTreeNodeType());
            vo.setPretendNodeType(node.getPretendNodeType());
            vo.setComment(node.getComment());
            vo.setIsLeaf(node.getIsLeaf());
            vo.setPinned(node.getPinned());
            vo.setParentPath(node.getParentPath());
            vo.setExtraParams(node.getExtraParams());
            voList.add(vo);
        }
        return voList;
    }
}