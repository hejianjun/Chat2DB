package ai.chat2db.server.domain.api.service;

import ai.chat2db.server.domain.api.param.CreateVirtualFKParam;
import ai.chat2db.server.domain.api.param.UpdateVirtualFKParam;
import ai.chat2db.spi.model.ForeignKey;
import ai.chat2db.spi.model.Table;
import ai.chat2db.spi.model.VirtualForeignKey;

import java.util.List;

public interface ForeignKeySyncService {

    SyncResult syncForeignKeys(Long dataSourceId, String databaseName, String schemaName, String tableName);

    List<ForeignKey> listAllForeignKeys(Long dataSourceId, String databaseName, String schemaName, String tableName);

    List<ForeignKey> listForeignKeys(Long dataSourceId, String databaseName, String schemaName, String tableName,
                                     ForeignKeyRelation relation);

    VirtualForeignKey createVirtualFK(CreateVirtualFKParam param);

    ImportVirtualFKResult importVirtualFKs(List<CreateVirtualFKParam> params);

    List<VirtualForeignKey> createInferredVirtualFKs(List<CreateVirtualFKParam> params);

    VirtualForeignKey updateVirtualFK(UpdateVirtualFKParam param);

    void deleteVirtualFK(Long id);

    int deleteVirtualFKs(List<Long> ids);

    String deleteRealFK(Long id);

    List<String> generateForeignKeyDDL(Table oldTable, Table newTable);

    List<ForeignKey> queryRealForeignKeys(Long dataSourceId, String databaseName, String schemaName, String tableName);

    List<VirtualForeignKey> queryVirtualForeignKeys(Long dataSourceId, String databaseName, String schemaName, String tableName);

    List<VirtualForeignKey> queryAllVirtualForeignKeys(Long dataSourceId, String databaseName, String schemaName);

    VirtualForeignKeyConsistencyResult validateVirtualForeignKeys(Long dataSourceId, String databaseName,
                                                                  String schemaName, String tableName,
                                                                  ForeignKeyRelation relation, List<Long> ids);

    int cleanInvalidVirtualForeignKeys(Long dataSourceId, String databaseName, String schemaName, List<String> existingTableNames);

    enum ForeignKeyRelation {
        SOURCE,
        REFERENCED,
        ANY
    }

    class SyncResult {
        private int added;
        private int deleted;
        private int unchanged;

        public SyncResult(int added, int deleted, int unchanged) {
            this.added = added;
            this.deleted = deleted;
            this.unchanged = unchanged;
        }

        public int getAdded() { return added; }
        public int getDeleted() { return deleted; }
        public int getUnchanged() { return unchanged; }
    }

    class ImportVirtualFKResult {
        private int created;
        private int updated;

        public ImportVirtualFKResult(int created, int updated) {
            this.created = created;
            this.updated = updated;
        }

        public int getCreated() { return created; }
        public int getUpdated() { return updated; }
    }

    class VirtualForeignKeyConsistencyResult {
        private int total;
        private int validCount;
        private double percentage;
        private List<VirtualForeignKeyConsistencyItem> items;

        public VirtualForeignKeyConsistencyResult(int total, int validCount,
                                                  List<VirtualForeignKeyConsistencyItem> items) {
            this.total = total;
            this.validCount = validCount;
            this.percentage = total == 0 ? 0 : validCount * 100.0 / total;
            this.items = items;
        }

        public int getTotal() { return total; }
        public int getValidCount() { return validCount; }
        public double getPercentage() { return percentage; }
        public List<VirtualForeignKeyConsistencyItem> getItems() { return items; }
    }

    class VirtualForeignKeyConsistencyItem {
        private Long id;
        private String tableName;
        private String columnName;
        private String referencedTable;
        private String referencedColumnName;
        private long orphanCount;
        private boolean valid;
        private String errorMessage;

        public VirtualForeignKeyConsistencyItem(Long id, String tableName, String columnName,
                                                String referencedTable, String referencedColumnName,
                                                long orphanCount, boolean valid, String errorMessage) {
            this.id = id;
            this.tableName = tableName;
            this.columnName = columnName;
            this.referencedTable = referencedTable;
            this.referencedColumnName = referencedColumnName;
            this.orphanCount = orphanCount;
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public Long getId() { return id; }
        public String getTableName() { return tableName; }
        public String getColumnName() { return columnName; }
        public String getReferencedTable() { return referencedTable; }
        public String getReferencedColumnName() { return referencedColumnName; }
        public long getOrphanCount() { return orphanCount; }
        public boolean isValid() { return valid; }
        public String getErrorMessage() { return errorMessage; }
    }
}
