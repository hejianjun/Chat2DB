package ai.chat2db.server.web.api.controller.rdb.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeleteFKResult {

    private String executedDDL;
}
