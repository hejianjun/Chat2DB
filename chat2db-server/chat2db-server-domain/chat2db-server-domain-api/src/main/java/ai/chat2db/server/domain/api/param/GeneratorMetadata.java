package ai.chat2db.server.domain.api.param;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeneratorMetadata implements Serializable {

    private static final long serialVersionUID = 1L;

    private String generatorType;

    private String label;

    private List<SubTypeOption> subTypes;

    private List<ConfigField> configFields;

    public static GeneratorMetadata of(String generatorType, String label) {
        return new GeneratorMetadata(generatorType, label, Collections.emptyList(), Collections.emptyList());
    }

    public static GeneratorMetadata of(String generatorType, String label, List<SubTypeOption> subTypes) {
        return new GeneratorMetadata(generatorType, label, subTypes, Collections.emptyList());
    }

    public static GeneratorMetadata full(String generatorType, String label, List<SubTypeOption> subTypes, List<ConfigField> configFields) {
        return new GeneratorMetadata(generatorType, label, subTypes, configFields);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubTypeOption implements Serializable {
        private String value;
        private String label;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfigField implements Serializable {
        private String key;
        private String label;
        private String type;
        private Object defaultValue;
    }
}
