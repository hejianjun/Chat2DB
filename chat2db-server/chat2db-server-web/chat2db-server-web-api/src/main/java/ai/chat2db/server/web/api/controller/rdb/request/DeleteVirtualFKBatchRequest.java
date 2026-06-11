package ai.chat2db.server.web.api.controller.rdb.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class DeleteVirtualFKBatchRequest {

    @NotEmpty
    private List<Long> ids;
}
