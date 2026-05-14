DROP TABLE IF EXISTS `data_generation_rule`;

CREATE TABLE IF NOT EXISTS `data_generation_config` (
    `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `data_source_id` bigint(20) unsigned NOT NULL COMMENT '数据源ID',
    `database_name` varchar(128) DEFAULT NULL COMMENT '数据库名',
    `schema_name` varchar(128) DEFAULT NULL COMMENT '模式名',
    `table_name` varchar(128) NOT NULL COMMENT '表名',
    `row_count` int unsigned NOT NULL DEFAULT 100 COMMENT '生成行数',
    `batch_size` int unsigned NOT NULL DEFAULT 1000 COMMENT '批处理大小',
    `user_id` bigint(20) unsigned NOT NULL DEFAULT 0 COMMENT '创建用户ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_generation_config` (`data_source_id`,`database_name`,`schema_name`,`table_name`),
    INDEX `idx_generation_config_data_source` (`data_source_id`),
    INDEX `idx_generation_config_user_source` (`user_id`,`data_source_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据生成配置表(表级配置)';

CREATE TABLE IF NOT EXISTS `data_generation_rule` (
    `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `data_source_id` bigint(20) unsigned NOT NULL COMMENT '数据源ID',
    `database_name` varchar(128) DEFAULT NULL COMMENT '数据库名',
    `schema_name` varchar(128) DEFAULT NULL COMMENT '模式名',
    `table_name` varchar(128) NOT NULL COMMENT '表名',
    `column_name` varchar(128) NOT NULL COMMENT '列名',
    `generation_type` varchar(64) NOT NULL COMMENT '数据生成大类: name, numeric, datetime, text, contact, address, business',
    `sub_type` varchar(64) DEFAULT NULL COMMENT '数据生成子类型: firstName, integer, date, email 等',
    `custom_params` text COMMENT '自定义参数(JSON格式)',
    `comment` varchar(512) DEFAULT NULL COMMENT '备注',
    `user_id` bigint(20) unsigned NOT NULL DEFAULT 0 COMMENT '创建用户ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_generation_rule` (`data_source_id`,`database_name`,`schema_name`,`table_name`,`column_name`),
    INDEX `idx_generation_rule_data_source` (`data_source_id`),
    INDEX `idx_generation_rule_user_source` (`user_id`,`data_source_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据生成规则表(列级配置)';
