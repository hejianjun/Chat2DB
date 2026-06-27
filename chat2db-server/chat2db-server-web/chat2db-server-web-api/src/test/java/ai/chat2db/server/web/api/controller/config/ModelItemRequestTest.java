package ai.chat2db.server.web.api.controller.config;

import ai.chat2db.server.web.api.controller.config.request.ModelItemRequest;
import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelItemRequestTest {

    @Test
    void preservesContextWindowTokensDuringJsonRoundTrip() {
        ModelItemRequest request = JSON.parseObject(
                "{\"name\":\"GPT\",\"model\":\"gpt-test\",\"contextWindowTokens\":131072}",
                ModelItemRequest.class);

        assertEquals(131072, request.getContextWindowTokens());
        assertEquals(131072, JSON.parseObject(JSON.toJSONString(request))
                .getInteger("contextWindowTokens"));
    }
}
