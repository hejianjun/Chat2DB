package ai.chat2db.server.web.api.controller.metadata;

import java.util.Set;

import ai.chat2db.server.domain.api.param.MetadataCommentParam;
import ai.chat2db.server.domain.api.service.MetadataCommentService;
import ai.chat2db.server.tools.base.excption.BusinessException;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.web.api.aspect.ConnectionInfoAspect;
import ai.chat2db.server.web.api.controller.metadata.request.MetadataCommentSaveRequest;
import jakarta.validation.Valid;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ConnectionInfoAspect
@RequestMapping("/api/metadata/comment")
@RestController
public class MetadataCommentController {

    private static final Set<String> SUPPORTED_TYPES = Set.of(
            MetadataCommentService.TYPE_DATABASE,
            MetadataCommentService.TYPE_SCHEMA,
            MetadataCommentService.TYPE_REDIS_PREFIX,
            MetadataCommentService.TYPE_REDIS_KEY
    );

    @Autowired
    private MetadataCommentService metadataCommentService;

    @PostMapping("/save")
    public ActionResult save(@Valid @RequestBody MetadataCommentSaveRequest request) {
        String objectType = StringUtils.upperCase(request.getObjectType());
        if (!SUPPORTED_TYPES.contains(objectType)) {
            throw new BusinessException("metadata.comment.unsupportedObjectType");
        }
        MetadataCommentParam param = MetadataCommentParam.builder()
                .dataSourceId(request.getDataSourceId())
                .objectType(objectType)
                .databaseName(request.getDatabaseName())
                .schemaName(request.getSchemaName())
                .objectName(StringUtils.defaultIfBlank(request.getObjectName(), request.getDatabaseName()))
                .comment(request.getComment())
                .aiComment(request.getAiComment())
                .build();
        return metadataCommentService.save(param);
    }
}
