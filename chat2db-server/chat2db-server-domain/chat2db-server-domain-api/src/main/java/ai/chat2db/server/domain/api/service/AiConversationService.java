package ai.chat2db.server.domain.api.service;

import ai.chat2db.server.domain.api.model.AiConversation;
import ai.chat2db.server.domain.api.model.AiConversationDetail;
import ai.chat2db.server.domain.api.param.ai.AiConversationCreateParam;
import ai.chat2db.server.domain.api.param.ai.AiConversationQueryParam;
import ai.chat2db.server.tools.base.wrapper.ServicePage;
import jakarta.validation.constraints.NotNull;


/**
 * AI 会话服务
 */
public interface AiConversationService {

    /**
     * 新建一个空会话
     *
     * @return conversationId
     */
    String create(@NotNull AiConversationCreateParam param);

    /**
     * 重命名
     */
    void updateTitle(@NotNull String conversationId, @NotNull String title);

    /**
     * 软删
     */
    void deleteWithPermission(@NotNull String conversationId, @NotNull Long userId);

    /**
     * 列表(分页,按用户隔离)
     */
    ServicePage<AiConversation> queryPage(@NotNull AiConversationQueryParam param);

    /**
     * 详情(含消息)
     */
    AiConversationDetail getDetail(@NotNull String conversationId, @NotNull Long userId);

    /**
     * 单条查询
     */
    AiConversation findByConversationId(@NotNull String conversationId);

    /**
     * Spring AI has persisted a completed user/assistant turn.
     */
    void updateAfterMessageTurn(@NotNull String conversationId, @NotNull String assistantContent);

    void rewindLastTurn(@NotNull String conversationId, @NotNull String expectedContent, boolean completedTurn);
}
