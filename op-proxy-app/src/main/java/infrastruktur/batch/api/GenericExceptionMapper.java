package infrastruktur.batch.api;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Catch-all-mapper för oväntade undantag. Bör inte nås under normal drift.
 * Loggar fullständig kontext för att underlätta felsökning.
 */
@Provider
public class GenericExceptionMapper implements ExceptionMapper<Exception> {
    private static final Logger LOG = LoggerFactory.getLogger(GenericExceptionMapper.class);

    @Override
    public Response toResponse(Exception exception) {
        LOG.error("Ohanterat fel", exception);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .type(MediaType.APPLICATION_JSON)
            .entity(Map.of(
                "error", "Internt serverfel",
                "code", "INTERNAL_ERROR",
                "message", "Ett oväntat fel uppstod"
            ))
            .build();
    }
}
