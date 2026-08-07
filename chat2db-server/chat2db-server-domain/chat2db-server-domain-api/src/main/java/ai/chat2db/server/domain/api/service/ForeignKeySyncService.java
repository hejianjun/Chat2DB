package ai.chat2db.server.domain.api.service;

import ai.chat2db.server.domain.api.param.CreateVirtualFKParam;
import ai.chat2db.server.domain.api.param.UpdateVirtualFKParam;
import ai.chat2db.spi.model.ForeignKey;
import ai.chat2db.spi.model.Table;
import ai.chat2db.spi.model.VirtualForeignKey;

import java.util.Collections;
import java.util.List;

public interface ForeignKeySyncService {

    SyncResult syncForeignKeys(Long dataSourceId, String databaseName, String schemaName, String tableName);

    List<ForeignKey> listAllForeignKeys(Long dataSourceId, String databaseName, String schemaName, String tableName);

    List<ForeignKey> listForeignKeys(Long dataSourceId, String databaseName, String schemaName, String tableName,
                                     ForeignKeyRelation relation);

    VirtualForeignKey createVirtualFK(CreateVirtualFKParam param);

    ImportVirtualFKResult importVirtualFKs(List<CreateVirtualFKParam> params, boolean skipInvalid);

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

    VirtualForeignKeyConsistencyResult validateVirtualForeignKeys(Long dataSourceId, String databaseName,
                                                                  String schemaName, String tableName,
                                                                  ForeignKeyRelation relation, List<Long> ids,
                                                                  VirtualForeignKeyConsistencyListener listener);

    int cleanInvalidVirtualForeignKeys(Long dataSourceId, String databaseName, String schemaName, List<String> existingTableNames);

    enum ForeignKeyRelation {
        SOURCE,
        REFERENCED,
        ANY
    }

    interface VirtualForeignKeyConsistencyListener {
        void onStart(int total);

        void onItem(int processed, int validCount, VirtualForeignKeyConsistencyItem item);
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
        private List<ImportVirtualFKInvalidItem> invalidItems;

        public ImportVirtualFKResult(int created, int updated) {
            this(created, updated, Collections.emptyList());
        }

        public ImportVirtualFKResult(int created, int updated, List<ImportVirtualFKInvalidItem> invalidItems) {
            this.created = created;
            this.updated = updated;
            this.invalidItems = invalidItems;
        }

        public int getCreated() { return created; }
        public int getUpdated() { return updated; }
        public List<ImportVirtualFKInvalidItem> getInvalidItems() { return invalidItems; }
    }

    class ImportVirtualFKInvalidItem {
        private String tableName;
        private String columnName;
        private String referencedTable;
        private String referencedColumnName;
        private List<ImportVirtualFKErrorType> errorTypes;

        public ImportVirtualFKInvalidItem(String tableName, String columnName,
                                          String referencedTable, String referencedColumnName,
                                          List<ImportVirtualFKErrorType> errorTypes) {
            this.tableName = tableName;
            this.columnName = columnName;
            this.referencedTable = referencedTable;
            this.referencedColumnName = referencedColumnName;
            this.errorTypes = errorTypes;
        }

        public String getTableName() { return tableName; }
        public String getColumnName() { return columnName; }
        public String getReferencedTable() { return referencedTable; }
        public String getReferencedColumnName() { return referencedColumnName; }
        public List<ImportVirtualFKErrorType> getErrorTypes() { return errorTypes; }
    }

    enum ImportVirtualFKErrorType {
        SOURCE_TABLE_NOT_FOUND,
        SOURCE_COLUMN_NOT_FOUND,
        REFERENCED_TABLE_NOT_FOUND,
        REFERENCED_COLUMN_NOT_FOUND
    }

    class VirtualForeignKeyConsistencyResult {
        private int total;
        private int validCount;
        private double percentage;
        private long rowTotalCount;
        private long rowValidCount;
        private long rowOrphanCount;
        private double rowPercentage;
        private List<VirtualForeignKeyConsistencyItem> items;

        public VirtualForeignKeyConsistencyResult(int total, int validCount,
                                                  List<VirtualForeignKeyConsistencyItem> items) {
            this.total = total;
            this.validCount = validCount;
            this.percentage = total == 0 ? 0 : validCount * 100.0 / total;
            this.items = items;
            this.rowTotalCount = items.stream().mapToLong(VirtualForeignKeyConsistencyItem::getTotalCount).sum();
            this.rowValidCount = items.stream().mapToLong(VirtualForeignKeyConsistencyItem::getValidCount).sum();
            this.rowOrphanCount = items.stream().mapToLong(VirtualForeignKeyConsistencyItem::getOrphanCount).sum();
            this.rowPercentage = rowTotalCount == 0 ? 0 : rowValidCount * 100.0 / rowTotalCount;
        }

        public int getTotal() { return total; }
        public int getValidCount() { return validCount; }
        public double getPercentage() { return percentage; }
        public long getRowTotalCount() { return rowTotalCount; }
        public long getRowValidCount() { return rowValidCount; }
        public long getRowOrphanCount() { return rowOrphanCount; }
        public double getRowPercentage() { return rowPercentage; }
        public List<VirtualForeignKeyConsistencyItem> getItems() { return items; }
    }

    class VirtualForeignKeyConsistencyItem {
        private Long id;
        private String tableName;
        private String columnName;
        private String referencedTable;
        private String referencedColumnName;
        private long totalCount;
        private long validCount;
        private long orphanCount;
        private double percentage;
        private boolean valid;
        private String errorMessage;

        public VirtualForeignKeyConsistencyItem(Long id, String tableName, String columnName,
                                                String referencedTable, String referencedColumnName,
                                                long totalCount, long validCount, long orphanCount,
                                                boolean valid, String errorMessage) {
            this.id = id;
            this.tableName = tableName;
            this.columnName = columnName;
            this.referencedTable = referencedTable;
            this.referencedColumnName = referencedColumnName;
            this.totalCount = totalCount;
            this.validCount = validCount;
            this.orphanCount = orphanCount;
            this.percentage = totalCount == 0 ? 0 : validCount * 100.0 / totalCount;
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public Long getId() { return id; }
        public String getTableName() { return tableName; }
        public String getColumnName() { return columnName; }
        public String getReferencedTable() { return referencedTable; }
        public String getReferencedColumnName() { return referencedColumnName; }
        public long getTotalCount() { return totalCount; }
        public long getValidCount() { return validCount; }
        public long getOrphanCount() { return orphanCount; }
        public double getPercentage() { return percentage; }
        public boolean isValid() { return valid; }
        public String getErrorMessage() { return errorMessage; }
    }
}
