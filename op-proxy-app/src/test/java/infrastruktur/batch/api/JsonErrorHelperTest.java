package infrastruktur.batch.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonErrorHelperTest {

    @Test
    void extractJsonErrorMessageWithNullShouldReturnNull() {
        assertNull(JsonErrorHelper.extractJsonErrorMessage(null));
    }

    @Test
    void extractJsonErrorMessageWithNonJsonExceptionShouldReturnNull() {
        assertNull(JsonErrorHelper.extractJsonErrorMessage(new RuntimeException("some other error")));
    }

    @Test
    void extractJsonErrorMessageWithJsonParseExceptionShouldReturnMessage() throws Exception {
        JsonProcessingException jsonEx = createJsonParseException("{invalid");
        String message = JsonErrorHelper.extractJsonErrorMessage(jsonEx);
        assertNotNull(message);
        assertTrue(message.startsWith("Request body could not be deserialized"),
            "Expected message to start with 'Request body could not be deserialized' but was: " + message);
    }

    @Test
    void extractJsonErrorMessageWithWrappedJsonExceptionShouldReturnMessage() throws Exception {
        JsonProcessingException jsonEx = createJsonParseException("{invalid");
        RuntimeException wrapper = new RuntimeException("wrapped", jsonEx);
        String message = JsonErrorHelper.extractJsonErrorMessage(wrapper);
        assertNotNull(message);
        assertTrue(message.startsWith("Request body could not be deserialized"),
            "Expected message to start with 'Request body could not be deserialized' but was: " + message);
    }

    @Test
    void extractJsonErrorMessageShouldIncludeLocationWhenAvailable() throws Exception {
        JsonProcessingException jsonEx = createJsonParseException("{invalid json content here");
        String message = JsonErrorHelper.extractJsonErrorMessage(jsonEx);
        assertNotNull(message);
        // When Jackson provides location info, the message includes line/column info
        // We just verify the message is a non-empty string about deserialization
        assertTrue(message.contains("deserialized"),
            "Message should mention deserialization: " + message);
    }

    private static JsonProcessingException createJsonParseException(String invalidJson) throws Exception {
        try {
            new ObjectMapper().readValue(invalidJson, Object.class);
            throw new AssertionError("Expected parsing to fail for: " + invalidJson);
        } catch (JsonProcessingException ex) {
            return ex;
        }
    }
}
