package ai.chat2db.plugin.phoenix.type;

import java.util.Arrays;
import java.util.List;

import ai.chat2db.spi.model.DefaultValue;

public enum PhoenixDefaultValueEnum {
    EMPTY_STRING("EMPTY_STRING"),
    NULL("NULL"),
    ;
    private DefaultValue defaultValue;

    PhoenixDefaultValueEnum(String defaultValue) {
        this.defaultValue = new DefaultValue(defaultValue);
    }


    public DefaultValue getDefaultValue() {
        return defaultValue;
    }

    public static List<DefaultValue> getDefaultValues() {
        return Arrays.stream(PhoenixDefaultValueEnum.values()).map(PhoenixDefaultValueEnum::getDefaultValue).collect(java.util.stream.Collectors.toList());
    }

}
