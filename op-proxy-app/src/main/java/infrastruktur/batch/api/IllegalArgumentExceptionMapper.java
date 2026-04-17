package infrastruktur.batch.api;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@Provider
public class IllegalArgumentExceptionMapper implements ExceptionMapper<IllegalArgumentException> {
    private static final Logger LOG = LoggerFactory.getLogger(IllegalArgumentExceptionMapper.class);

    @Override
    public Response toResponse(IllegalArgumentException exception) {
        LOG.warn("Invalid request payload or parameter: {}", exception.getMessage());
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(Map.of(
                "error", "Invalid request",
                "code", "BAD_REQUEST"
            ))
            .build();
    }
}
