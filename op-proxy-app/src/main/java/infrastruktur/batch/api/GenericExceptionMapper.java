package infrastruktur.batch.api;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Catch-all mapper for unexpected exceptions. Should not be reached in normal operation.
 * Logs full context to help diagnose issues.
 */
@Provider
public class GenericExceptionMapper implements ExceptionMapper<Exception> {
    private static final Logger LOG = LoggerFactory.getLogger(GenericExceptionMapper.class);

    @Override
    public Response toResponse(Exception exception) {
        LOG.error("Unhandled error", exception);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .type(MediaType.APPLICATION_JSON)
            .entity(Map.of(
                "error", "Internal server error",
                "code", "INTERNAL_ERROR",
                "message", "An unexpected error occurred"
            ))
            .build();
    }
}
