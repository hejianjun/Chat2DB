DROP TABLE IF EXISTS `data_generation_rule`;

CREATE TABLE IF NOT EXISTS `data_generation_rule` (
    `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `data_source_id` bigint(20) unsigned NOT NULL COMMENT '数据源ID',
    `database_name` varchar(128) DEFAULT NULL COMMENT '数据库名',
    `schema_name` varchar(128) DEFAULT NULL COMMENT '模式名',
    `table_name` varchar(128) NOT NULL COMMENT '表名',
    `row_count` int unsigned NOT NULL DEFAULT 100 COMMENT '生成行数',
    `column_configs` text COMMENT '列配置JSON: [{"columnName":"id","expression":"..."},{"columnName":"name","expression":"..."}]',
    `user_id` bigint(20) unsigned NOT NULL DEFAULT 0 COMMENT '创建用户ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_generation_rule` (`data_source_id`,`database_name`,`schema_name`,`table_name`),
    INDEX `idx_generation_rule_data_source` (`data_source_id`),
    INDEX `idx_generation_rule_user_source` (`user_id`,`data_source_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据生成规则表';
