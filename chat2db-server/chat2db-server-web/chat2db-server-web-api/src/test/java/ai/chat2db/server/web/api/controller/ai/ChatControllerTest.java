package ai.chat2db.server.web.api.controller.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;

import ai.chat2db.server.web.api.controller.ai.enums.PromptType;
import ai.chat2db.server.web.api.controller.ai.request.ChatImageRequest;
import ai.chat2db.server.web.api.controller.ai.request.ChatQueryRequest;
import ai.chat2db.server.web.api.controller.ai.statemachine.ChatEvent;
import ai.chat2db.server.tools.common.exception.ParamBusinessException;

class ChatControllerTest {

    private final ChatController controller = new ChatController();

    @Test
    void sqlCompletionWithTablesFetchesSchema() {
        ChatQueryRequest request = request(PromptType.SQL_COMPLETION);
        request.setTableNames(List.of("vehicle", "vehicle_sim"));

        assertEquals(ChatEvent.TABLES_PROVIDED, controller.determineInitialEvent(request));
    }

    @Test
    void sqlCompletionWithoutTablesSkipsAutomaticSelection() {
        ChatQueryRequest request = request(PromptType.SQL_COMPLETION);

        assertEquals(ChatEvent.TABLES_NOT_NEEDED, controller.determineInitialEvent(request));
    }

    @Test
    void nl2SqlWithoutTablesStillUsesAutomaticSelection() {
        ChatQueryRequest request = request(PromptType.NL_2_SQL);

        assertEquals(ChatEvent.TABLES_NOT_PROVIDED, controller.determineInitialEvent(request));
    }

    @Test
    void sqlExplainKeepsExplainSelectionFlow() {
        ChatQueryRequest request = request(PromptType.SQL_EXPLAIN);

        assertEquals(ChatEvent.EXPLAIN_TABLES_NOT_SELECTED, controller.determineInitialEvent(request));
    }

    @Test
    void validImagePayloadCanBeCreated() {
        ChatQueryRequest request = request(PromptType.NL_2_SQL);
        request.setImages(List.of(image("image/png", Base64.getEncoder().encodeToString(new byte[] {1, 2, 3}))));

        assertEquals(200, controller.createChatPayload(request).getStatusCode().value());
    }

    @Test
    void unsupportedImageTypeIsRejected() {
        ChatQueryRequest request = request(PromptType.NL_2_SQL);
        request.setImages(List.of(image("image/svg+xml", Base64.getEncoder().encodeToString(new byte[] {1}))));

        assertThrows(ParamBusinessException.class, () -> controller.createChatPayload(request));
    }

    private ChatQueryRequest request(PromptType promptType) {
        ChatQueryRequest request = new ChatQueryRequest();
        request.setPromptType(promptType.getCode());
        return request;
    }

    private ChatImageRequest image(String mimeType, String data) {
        ChatImageRequest image = new ChatImageRequest();
        image.setName("test");
        image.setMimeType(mimeType);
        image.setData(data);
        return image;
    }
}
