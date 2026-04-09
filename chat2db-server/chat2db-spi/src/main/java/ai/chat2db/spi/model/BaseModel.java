package ai.chat2db.spi.model;

public interface BaseModel<T> {
    /**
     * 获取数据库名称
     * 
     * @return
     */
    String getDatabaseName();

    /*
     * 获取schema名称
     */
    String getSchemaName();

    /**
     * 获取表名
     * 
     * @return
     */
    String getTableName();

    /**
     * 获取类型
     * @return
     */
    Class<? extends T> getClassType();

    /**
     * 设置数据库名称
     * 
     * @param databaseName
     */
    void setDatabaseName(String databaseName);

    /**
     * 设置schema名称
     * 
     * @param schemaName
     */
    void setSchemaName(String schemaName);

    /**
     * 设置表名称
     * 
     * @param tableName
     */
    void setTableName(String tableName);
}
