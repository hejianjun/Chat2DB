package ai.chat2db.server.web.api.controller.ai.converter;

import ai.chat2db.server.domain.api.param.TableQueryParam;
import ai.chat2db.server.web.api.controller.ai.request.ChatQueryRequest;

import com.unfbx.chatgpt.entity.common.Usage;
import com.unfbx.chatgpt.entity.embeddings.EmbeddingResponse;
import com.unfbx.chatgpt.entity.embeddings.Item;
import org.mapstruct.Mapper;

/**
 * @author moji
 * @version ChatConverter.java, v 0.1 2023年04月02日 13:31 moji Exp $
 * @date 2023/04/02
 */
@Mapper(componentModel = "spring")
public abstract class ChatConverter {

    /**
     * 参数转换
     *
     * @param request
     * @return
     */
    public abstract TableQueryParam chat2tableQuery(ChatQueryRequest request);

}
