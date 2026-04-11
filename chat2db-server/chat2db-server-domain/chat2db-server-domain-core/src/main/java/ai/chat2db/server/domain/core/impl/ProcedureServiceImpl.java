package ai.chat2db.server.domain.core.impl;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ai.chat2db.server.domain.api.model.TreeNode;
import ai.chat2db.server.domain.api.param.TreeSearchParam;
import ai.chat2db.server.domain.api.service.ProcedureService;
import ai.chat2db.server.domain.core.cache.LuceneIndexManager;
import ai.chat2db.server.domain.core.cache.LuceneIndexManagerFactory;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.model.Procedure;
import ai.chat2db.spi.sql.Chat2DBContext;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ProcedureServiceImpl implements ProcedureService {

    @Autowired
    private LuceneIndexManagerFactory managerFactory;

    @Override
    public ListResult<Procedure> procedures(String databaseName, String schemaName) {
        return ListResult.of(Chat2DBContext.getMetaData().procedures(Chat2DBContext.getConnection(),databaseName, schemaName));
    }

    @Override
    public DataResult<Procedure> detail(String databaseName, String schemaName, String procedureName) {
        return DataResult.of(Chat2DBContext.getMetaData().procedure(Chat2DBContext.getConnection(), databaseName, schemaName, procedureName));
    }

    @Override
    public List<TreeNode> searchTreeNodes(TreeSearchParam param) {
        LuceneIndexManager<Procedure> mgr = managerFactory.getManager(param.getDataSourceId());
        Procedure queryModel = Procedure.builder()
                .databaseName(param.getDatabaseName())
                .schemaName(param.getSchemaName())
                .build();
        Long version = mgr.getMaxVersion(queryModel);

        if (param.isRefresh() || version == null) {
            loadAndCacheMetadata(mgr, param, version);
        }

        List<Procedure> procedures = mgr.search(queryModel, null, param.getSearchKey());
        List<TreeNode> result = new ArrayList<>();
        for (Procedure procedure : procedures) {
            TreeNode node = buildTreeNode(procedure);
            result.add(node);
        }
        return result;
    }

    private void loadAndCacheMetadata(LuceneIndexManager<Procedure> mgr, TreeSearchParam param, Long version) {
        mgr.getLock().writeLock().lock();
        try {
            Connection conn = Chat2DBContext.getConnection();
            MetaData meta = Chat2DBContext.getMetaData();
            List<Procedure> procedures = meta.procedures(conn, param.getDatabaseName(), param.getSchemaName());
            if (CollectionUtils.isEmpty(procedures)) {
                return;
            }
            procedures.forEach(p -> p.setVersion(version));
            mgr.updateDocuments(procedures, version);
        } catch (Exception e) {
            log.error("loadAndCacheMetadata error,version:{}", version, e);
        } finally {
            mgr.getLock().writeLock().unlock();
        }
    }

    private TreeNode buildTreeNode(Procedure procedure) {
        List<String> parentPath = new ArrayList<>();
        if (StringUtils.isNotBlank(procedure.getDatabaseName())) {
            parentPath.add(procedure.getDatabaseName());
        }
        if (StringUtils.isNotBlank(procedure.getSchemaName())) {
            parentPath.add(procedure.getSchemaName());
        }

        Map<String, Object> extraParams = new HashMap<>();
        extraParams.put("databaseName", procedure.getDatabaseName());
        extraParams.put("schemaName", procedure.getSchemaName());
        extraParams.put("procedureName", procedure.getName());

        return TreeNode.builder()
                .uuid("procedure-" + procedure.getName())
                .key(procedure.getName())
                .name(procedure.getName())
                .treeNodeType("PROCEDURE")
                .comment(procedure.getComment())
                .isLeaf(true)
                .parentPath(parentPath)
                .extraParams(extraParams)
                .build();
    }
}