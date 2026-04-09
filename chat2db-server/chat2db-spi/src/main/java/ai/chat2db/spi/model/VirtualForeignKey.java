package ai.chat2db.spi.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class VirtualForeignKey extends ForeignKey {

    /**
     * 额外的虚拟外键属性
     */
    private String virtualProperty;
}
