package infrastruktur.batch.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@Provider
public class WebApplicationExceptionMapper implements ExceptionMapper<WebApplicationException> {
    private static final Logger LOG = LoggerFactory.getLogger(WebApplicationExceptionMapper.class);

    @Override
    public Response toResponse(WebApplicationException exception) {
        Response exceptionResponse = exception.getResponse();
        int status = exceptionResponse == null ? Response.Status.INTERNAL_SERVER_ERROR.getStatusCode() : exceptionResponse.getStatus();

        String deserializationMessage = extractDeserializationMessage(exception);
        if (status == Response.Status.BAD_REQUEST.getStatusCode() && deserializationMessage != null) {
            LOG.warn("Invalid request body: {}", deserializationMessage);
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of(
                    "error", "Invalid request body",
                    "code", "BAD_REQUEST",
                    "message", deserializationMessage
                ))
                .build();
        }

        Response.Status statusEnum = Response.Status.fromStatusCode(status);
        String code = statusEnum == null ? "HTTP_" + status : statusEnum.name();
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = statusEnum == null ? "HTTP request failed" : statusEnum.getReasonPhrase();
        }

        if (status >= 500) {
            LOG.error("HTTP exception mapped to status {}: {}", status, message, exception);
        } else {
            LOG.info("HTTP exception mapped to status {}: {}", status, message);
        }

        return Response.status(status)
            .entity(Map.of(
                "error", "Request failed",
                "code", code,
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