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
        if (lowerDataType.contains("varchar") || lowerDataType.contains("text") || lowerDataType.contains("char")) {
            return generatorMap.get("text");
        } else if (lowerDataType.contains("int") || lowerDataType.contains("decimal") || lowerDataType.contains("numeric")
                || lowerDataType.contains("float") || lowerDataType.contains("double")) {
            return generatorMap.get("numeric");
        } else if (lowerDataType.contains("date") || lowerDataType.contains("time") || lowerDataType.contains("timestamp")) {
            return generatorMap.get("datetime");
        } else {
            return generatorMap.get("text");
        }
    }

    public Faker createFaker() {
        return new Faker();
    }
}
