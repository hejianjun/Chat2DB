package ai.chat2db.server.domain.core.generator;

import ai.chat2db.server.domain.core.generator.impl.*;
import net.datafaker.Faker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据生成器工厂
 */
@Component
public class DataGeneratorFactory {

    @Autowired
    private List<DataGenerator> generators;

    private Map<String, DataGenerator> generatorMap;

    /**
     * 初始化生成器映射
     */
    private void initGeneratorMap() {
        if (generatorMap == null) {
            generatorMap = new HashMap<>();
            for (DataGenerator generator : generators) {
                generatorMap.put(generator.getGeneratorType(), generator);
            }
        }
    }

    /**
     * 根据生成类型获取数据生成器
     *
     * @param generationType 生成类型
     * @return 数据生成器
     */
    public DataGenerator getGenerator(String generationType) {
        initGeneratorMap();
        return generatorMap.get(generationType);
    }

    /**
     * 根据数据类型推断默认生成器
     *
     * @param dataType 数据类型
     * @return 数据生成器
     */
    public DataGenerator getDefaultGenerator(String dataType) {
        initGeneratorMap();
        
        String lowerDataType = dataType.toLowerCase();
        
        // 根据数据类型推断生成器
        if (lowerDataType.contains("varchar") || lowerDataType.contains("text") || lowerDataType.contains("char")) {
            return generatorMap.get("text");
        } else if (lowerDataType.contains("int") || lowerDataType.contains("decimal") || lowerDataType.contains("numeric") || 
                   lowerDataType.contains("float") || lowerDataType.contains("double")) {
            return generatorMap.get("numeric");
        } else if (lowerDataType.contains("date") || lowerDataType.contains("time") || lowerDataType.contains("timestamp")) {
            return generatorMap.get("datetime");
        } else {
            // 默认使用文本生成器
            return generatorMap.get("text");
        }
    }

    /**
     * 获取所有支持的生成类型
     *
     * @return 生成类型列表
     */
    public String[] getSupportedTypes() {
        initGeneratorMap();
        return generatorMap.keySet().toArray(new String[0]);
    }

    /**
     * 创建Faker实例
     *
     * @return Faker实例
     */
    public Faker createFaker() {
        return new Faker();
    }
}
