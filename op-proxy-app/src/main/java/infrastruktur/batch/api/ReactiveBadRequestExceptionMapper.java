package infrastruktur.batch.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class ReactiveBadRequestExceptionMapper {
    private static final Logger LOG = LoggerFactory.getLogger(ReactiveBadRequestExceptionMapper.class);

    @ServerExceptionMapper
    public Response mapBadRequest(BadRequestException exception) {
        String message = extractDeserializationMessage(exception);
        if (message == null || message.isBlank()) {
            message = "Invalid request body";
        }

        LOG.warn("Bad request: {}", message);
        return Response.status(Response.Status.BAD_REQUEST)
            .type(MediaType.APPLICATION_JSON)
            .entity(Map.of(
                "error", "Invalid request body",
                "code", "BAD_REQUEST",
                "message", message
            ))
            .build();
    }

    private String extractDeserializationMessage(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof JsonProcessingException jsonException) {
                String location = jsonException.getLocation() == null
                    ? ""
                    : " (line " + jsonException.getLocation().getLineNr()
                        + ", column " + jsonException.getLocation().getColumnNr() + ")";
                return "Request body could not be deserialized" + location + ": " + jsonException.getOriginalMessage();
            }
            current = current.getCause();
        }
        return null;
    }
}
