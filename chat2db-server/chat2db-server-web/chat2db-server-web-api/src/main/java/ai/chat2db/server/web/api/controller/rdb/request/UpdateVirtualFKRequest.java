package ai.chat2db.server.web.api.controller.rdb.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateVirtualFKRequest {

    @NotNull
    private Long id;

    private String comment;

    private String referencedTable;

    private String referencedColumnName;

    private String vkName;
}
