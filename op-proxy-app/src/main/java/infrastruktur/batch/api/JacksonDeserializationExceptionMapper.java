package infrastruktur.batch.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * DEPRECATED: Use JsonErrorHelper utility instead.
 *
 * This class is kept for reference but is no longer registered as a @Provider.
 * Deserialization errors are now handled uniformly through
 * IllegalArgumentExceptionMapper by having resource methods deserialize
 * explicitly and throw IllegalArgumentException on malformed input.
 *
 * Deserialization failures map to HTTP 400 Bad Request with structured JSON:
 * {
 *   "error": "Invalid request",
 *   "code": "BAD_REQUEST",
 *   "message": "Request body could not be deserialized ..."
 * }
 */
public class JacksonDeserializationExceptionMapper implements ExceptionMapper<JsonProcessingException> {

    private static final Logger LOG = LoggerFactory.getLogger(JacksonDeserializationExceptionMapper.class);

    @Override
    public Response toResponse(JsonProcessingException exception) {
        // NOT REGISTERED - this method should not be called.
        throw new UnsupportedOperationException(
            "JacksonDeserializationExceptionMapper is deprecated. " +
            "Use JsonErrorHelper instead, or let resources deserialize explicitly."
        );
    }
}
