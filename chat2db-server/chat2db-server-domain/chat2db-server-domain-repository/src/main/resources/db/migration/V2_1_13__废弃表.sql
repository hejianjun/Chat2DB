CREATE TABLE IF NOT EXISTS `deprecated_table` (
    `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `data_source_id` bigint(20) unsigned NOT NULL COMMENT '数据源连接ID',
    `database_name` varchar(128) DEFAULT NULL COMMENT 'db名称',
    `schema_name` varchar(128) DEFAULT NULL COMMENT 'schema名称',
    `table_name` varchar(128) DEFAULT NULL COMMENT 'table_name',
    `user_id` bigint(20) unsigned NOT NULL DEFAULT 0 COMMENT '用户id',
    PRIMARY KEY (`id`)
    ) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='DEPRECATED TABLES'
;
create INDEX idx_user_id_data_source_id_deprecated on deprecated_table(user_id,data_source_id) ;
