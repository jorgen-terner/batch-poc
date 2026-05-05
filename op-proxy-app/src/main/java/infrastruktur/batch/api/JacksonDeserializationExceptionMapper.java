package infrastruktur.batch.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Maps Jackson deserialization failures (malformed JSON, wrong types, missing
 * object wrappers, etc.) to a structured HTTP 400 response.
 *
 * <p>Without this mapper the RESTEasy Reactive {@code RequestDeserializeHandler}
 * may produce an unformatted 400 or even a 500 that leaks internal detail. This
 * mapper intentionally suppresses the full stack trace in the response to avoid
 * information leakage while still logging enough context for debugging.
 */
@Provider
public class JacksonDeserializationExceptionMapper implements ExceptionMapper<JsonProcessingException> {

    private static final Logger LOG = LoggerFactory.getLogger(JacksonDeserializationExceptionMapper.class);

    @Override
    public Response toResponse(JsonProcessingException exception) {
        // Log the location (line/column) but not the full payload value to avoid
        // leaking sensitive content in logs.
        String location = exception.getLocation() != null
                ? " (line " + exception.getLocation().getLineNr()
                  + ", column " + exception.getLocation().getColumnNr() + ")"
                : "";
        LOG.warn("Request body could not be deserialized{}: {}", location, exception.getOriginalMessage());
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of(
                        "error", "Invalid request body",
                        "code", "BAD_REQUEST",
                        "message", "Request body could not be deserialized" + location
                                   + ": " + exception.getOriginalMessage()
                ))
                .build();
    }
}
