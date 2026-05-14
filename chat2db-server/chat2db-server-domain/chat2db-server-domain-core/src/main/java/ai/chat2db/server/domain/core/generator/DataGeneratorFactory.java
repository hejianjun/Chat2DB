package ai.chat2db.server.domain.core.generator;

import ai.chat2db.server.domain.api.param.GeneratorMetadata;
import ai.chat2db.server.domain.core.generator.impl.*;
import net.datafaker.Faker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class DataGeneratorFactory {

    @Autowired
    private List<DataGenerator> generators;

    private Map<String, DataGenerator> generatorMap;

    private List<GeneratorMetadata> allMetadata;

    private void init() {
        if (generatorMap != null) {
            return;
        }
        generatorMap = new HashMap<>();
        allMetadata = new ArrayList<>();
        for (DataGenerator generator : generators) {
            GeneratorMetadata metadata = generator.getMetadata();
            generatorMap.put(metadata.getGeneratorType(), generator);
            allMetadata.add(metadata);
        }
    }

    public DataGenerator getGenerator(String generatorType) {
        init();
        return generatorMap.get(generatorType);
    }

    public List<GeneratorMetadata> getAllMetadata() {
        init();
        return allMetadata;
    }

    public DataGenerator getDefaultGenerator(String dataType) {
        init();
        String lowerDataType = dataType.toLowerCase();
        // 字符串类型：VARCHAR, TEXT, CHAR, TINYTEXT, MEDIUMTEXT, LONGTEXT, ENUM, SET
        if (lowerDataType.contains("varchar") || lowerDataType.contains("text") || lowerDataType.contains("char") 
                || lowerDataType.contains("tinytext") || lowerDataType.contains("mediumtext") 
                || lowerDataType.contains("longtext") || lowerDataType.contains("enum") 
                || lowerDataType.contains("set")) {
            return generatorMap.get("text");
        } 
        // 数值类型：INT, DECIMAL, NUMERIC, FLOAT, DOUBLE, TINYINT, SMALLINT, MEDIUMINT, BIGINT, BIT, BOOL, INTEGER, REAL
        else if (lowerDataType.contains("int") || lowerDataType.contains("decimal") || lowerDataType.contains("numeric")
                || lowerDataType.contains("float") || lowerDataType.contains("double") 
                || lowerDataType.contains("tinyint") || lowerDataType.contains("smallint") 
                || lowerDataType.contains("mediumint") || lowerDataType.contains("bigint") 
                || lowerDataType.contains("bit") || lowerDataType.contains("bool") 
                || lowerDataType.contains("integer") || lowerDataType.contains("real")) {
            return generatorMap.get("numeric");
        } 
        // 日期时间类型：DATE, TIME, TIMESTAMP, DATETIME, YEAR
        else if (lowerDataType.contains("date") || lowerDataType.contains("time") 
                || lowerDataType.contains("timestamp") || lowerDataType.contains("datetime") 
                || lowerDataType.contains("year")) {
            return generatorMap.get("datetime");
        } 
        // 二进制类型：BINARY, VARBINARY, TINYBLOB, BLOB, MEDIUMBLOB, LONGBLOB
        else if (lowerDataType.contains("binary") || lowerDataType.contains("varbinary") 
                || lowerDataType.contains("tinyblob") || lowerDataType.contains("blob") 
                || lowerDataType.contains("mediumblob") || lowerDataType.contains("longblob")) {
            return generatorMap.get("text"); // 二进制数据暂时使用文本生成器
        } 
        // JSON类型
        else if (lowerDataType.contains("json")) {
            return generatorMap.get("text"); // JSON数据暂时使用文本生成器
        } 
        // 空间数据类型：GEOMETRY, POINT, LINESTRING, POLYGON等
        else if (lowerDataType.contains("geometry") || lowerDataType.contains("point") 
                || lowerDataType.contains("linestring") || lowerDataType.contains("polygon") 
                || lowerDataType.contains("multipoint") || lowerDataType.contains("multilinestring") 
                || lowerDataType.contains("multipolygon") || lowerDataType.contains("geometrycollection")) {
            return generatorMap.get("text"); // 空间数据暂时使用文本生成器
        } 
        // 默认使用文本生成器
        else {
            return generatorMap.get("text");
        }
    }

    public Faker createFaker() {
        return new Faker();
    }
}
