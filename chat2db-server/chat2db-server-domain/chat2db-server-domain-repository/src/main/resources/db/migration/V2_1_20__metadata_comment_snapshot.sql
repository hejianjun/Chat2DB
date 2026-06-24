CREATE TABLE IF NOT EXISTS `metadata_comment` (
    `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `data_source_id` bigint(20) unsigned NOT NULL COMMENT '数据源ID',
    `user_id` bigint(20) unsigned NOT NULL DEFAULT 0 COMMENT '用户ID，0表示全局',
    `object_type` varchar(32) NOT NULL COMMENT '对象类型',
    `object_key` varchar(1024) NOT NULL COMMENT '对象唯一key',
    `database_name` varchar(256) DEFAULT NULL COMMENT '数据库名',
    `schema_name` varchar(256) DEFAULT NULL COMMENT 'Schema名',
    `table_name` varchar(256) DEFAULT NULL COMMENT '表名',
    `column_name` varchar(256) DEFAULT NULL COMMENT '列名',
    `object_name` varchar(256) NOT NULL COMMENT '对象名',
    `comment` varchar(2048) DEFAULT NULL COMMENT '用户注释',
    `ai_comment` varchar(2048) DEFAULT NULL COMMENT 'AI注释',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_metadata_comment` (`data_source_id`,`user_id`,`object_type`,`object_key`),
    INDEX `idx_metadata_comment_query` (`data_source_id`,`user_id`,`object_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='元数据对象注释表';

CREATE TABLE IF NOT EXISTS `metadata_snapshot` (
    `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `data_source_id` bigint(20) unsigned NOT NULL COMMENT '数据源ID',
    `object_type` varchar(32) NOT NULL COMMENT '对象类型',
    `object_key` varchar(1024) NOT NULL COMMENT '对象唯一key',
    `database_name` varchar(256) DEFAULT NULL COMMENT '数据库名',
    `schema_name` varchar(256) DEFAULT NULL COMMENT 'Schema名',
    `object_name` varchar(256) NOT NULL COMMENT '对象名',
    `object_count` bigint(20) DEFAULT NULL COMMENT '对象数量',
    `version` bigint(20) DEFAULT NULL COMMENT '快照版本',
    `status` varchar(32) NOT NULL COMMENT 'READY/EMPTY/REFRESHING/FAILED',
    `refreshed_at` datetime DEFAULT NULL COMMENT '刷新时间',
    `error_message` varchar(1024) DEFAULT NULL COMMENT '错误信息',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_metadata_snapshot` (`data_source_id`,`object_type`,`object_key`),
    INDEX `idx_metadata_snapshot_query` (`data_source_id`,`object_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='元数据对象快照表';
