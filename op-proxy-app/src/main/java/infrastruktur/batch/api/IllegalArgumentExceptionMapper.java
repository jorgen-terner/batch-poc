package infrastruktur.batch.api;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Maps IllegalArgumentException to structured 400 Bad Request response.
 * Used by resource methods that validate request bodies or parameters.
 */
@Provider
public class IllegalArgumentExceptionMapper implements ExceptionMapper<IllegalArgumentException> {
    private static final Logger LOG = LoggerFactory.getLogger(IllegalArgumentExceptionMapper.class);

    @Override
    public Response toResponse(IllegalArgumentException exception) {
        LOG.warn("Invalid request: {}", exception.getMessage());
        String message = exception.getMessage() != null ? exception.getMessage() : "Invalid request";
        return Response.status(Response.Status.BAD_REQUEST)
            .type(MediaType.APPLICATION_JSON)
            .entity(Map.of(
                "error", "Invalid request",
                "code", "BAD_REQUEST",
                "message", message
            ))
            .build();
    }
}
