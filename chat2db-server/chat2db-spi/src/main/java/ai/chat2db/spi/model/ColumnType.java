package ai.chat2db.spi.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * ColumnType类用于表示数据库列的类型属性
 * 该类提供了各种布尔类型的属性，以指示列类型是否支持特定的特性
 */
@Data
@AllArgsConstructor
public class ColumnType {
    // 表示列类型的名称
    private String typeName;
    // 表示列类型是否支持指定的长度
    private boolean supportLength;
    // 表示列类型是否支持指定的小数位数
    private boolean supportScale;
    // 表示列类型是否支持是否可为空的特性
    private boolean supportNullable;
    // 表示列类型是否支持自动递增特性
    private boolean supportAutoIncrement;
    // 表示列类型是否支持指定字符集
    private boolean supportCharset;
    // 表示列类型是否支持排序规则
    private boolean supportCollation;
    // 表示列类型是否支持注释
    private boolean supportComments;
    // 表示列类型是否支持默认值
    private boolean supportDefaultValue;
    // 表示列类型是否支持扩展特性
    private boolean supportExtent;
    // 表示列类型是否支持值的特性
    private boolean supportValue;
    // 表示列类型是否支持单位的特性
    private boolean supportUnit;
}

