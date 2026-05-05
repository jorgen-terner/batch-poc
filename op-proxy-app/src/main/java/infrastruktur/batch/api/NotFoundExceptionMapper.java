package infrastruktur.batch.api;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Maps NoSuchElementException to structured 404 Not Found response.
 * Typically thrown when a required resource (job, execution, etc.) cannot be found.
 */
@Provider
public class NotFoundExceptionMapper implements ExceptionMapper<NoSuchElementException> {
    private static final Logger LOG = LoggerFactory.getLogger(NotFoundExceptionMapper.class);

    @Override
    public Response toResponse(NoSuchElementException exception) {
        LOG.info("Requested resource was not found: {}", exception.getMessage());
        String message = exception.getMessage() != null ? exception.getMessage() : "Resource not found";
        return Response.status(Response.Status.NOT_FOUND)
            .type(MediaType.APPLICATION_JSON)
            .entity(Map.of(
                "error", "Resource not found",
                "code", "NOT_FOUND",
                "message", message
            ))
            .build();
    }
}
