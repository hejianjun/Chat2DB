package ai.chat2db.server.domain.core.generator;

import ai.chat2db.server.domain.api.param.GeneratorMetadata;
import lombok.Data;
import net.datafaker.Faker;

import java.util.Map;

public interface DataGenerator {

    Object generate(Faker faker, ColumnConfig columnConfig);

    GeneratorMetadata getMetadata();

    @Data
    class ColumnConfig {
        private String columnName;
        private String dataType;
        private String subType;
        private String comment;
        private Boolean nullable;
        private Integer maxLength;
        private Integer scale;
        private Map<String, Object> customParams;
    }
}
