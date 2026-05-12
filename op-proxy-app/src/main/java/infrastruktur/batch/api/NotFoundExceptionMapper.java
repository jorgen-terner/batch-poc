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
 * Mappar NoSuchElementException till ett strukturerat 404-svar.
 * Kastas vanligtvis när en begärd resurs (jobb, körning o.s.v.) inte kan hittas.
 */
@Provider
public class NotFoundExceptionMapper implements ExceptionMapper<NoSuchElementException> {
    private static final Logger LOG = LoggerFactory.getLogger(NotFoundExceptionMapper.class);

    @Override
    public Response toResponse(NoSuchElementException exception) {
        LOG.info("Efterfrågad resurs hittades inte: {}", exception.getMessage());
        String message = exception.getMessage() != null ? exception.getMessage() : "Resursen hittades inte";
        return Response.status(Response.Status.NOT_FOUND)
            .type(MediaType.APPLICATION_JSON)
            .entity(Map.of(
                "error", "Resursen hittades inte",
                "code", "NOT_FOUND",
                "message", message
            ))
            .build();
    }
}
