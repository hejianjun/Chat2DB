import mysqlService from '@/service/sql';

export const deprecatedTable = ({ treeNodeData, loadData }: { treeNodeData: any; loadData: any }) => {
  mysqlService.deprecatedTable({
    dataSourceId: treeNodeData.extraParams.dataSourceId,
    databaseName: treeNodeData.extraParams.databaseName,
    schemaName: treeNodeData.extraParams.schemaName,
    tableName: treeNodeData.name,
  }).then(() => {
    loadData();
  });
};

export const restoreDeprecatedTable = ({ treeNodeData, loadData }: { treeNodeData: any; loadData: any }) => {
  mysqlService.restoreDeprecatedTable({
    dataSourceId: treeNodeData.extraParams.dataSourceId,
    databaseName: treeNodeData.extraParams.databaseName,
    schemaName: treeNodeData.extraParams.schemaName,
    tableName: treeNodeData.name,
  }).then(() => {
    loadData();
  });
};
