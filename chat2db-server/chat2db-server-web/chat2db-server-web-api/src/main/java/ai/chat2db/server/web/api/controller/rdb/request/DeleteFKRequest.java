package ai.chat2db.server.web.api.controller.rdb.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DeleteFKRequest {

    @NotNull
    private Long id;

    @NotBlank
    private String sourceType;
}
