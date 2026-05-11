package infrastruktur.batch.api;

import infrastruktur.batch.service.TemplateProcessingException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Maps TemplateProcessingException to 422 Unprocessable Entity.
 * Thrown when template processing fails (template not found, invalid template format, etc.).
 */
@Provider
public class TemplateProcessingExceptionMapper implements ExceptionMapper<TemplateProcessingException> {
    private static final Logger LOG = LoggerFactory.getLogger(TemplateProcessingExceptionMapper.class);

    @Override
    public Response toResponse(TemplateProcessingException exception) {
        LOG.warn("Template processing misslyckades: {}", exception.getMessage());
        String message = exception.getMessage() != null ? exception.getMessage() : "Template processing misslyckades";
        return Response.status(422)
            .type(MediaType.APPLICATION_JSON)
            .entity(Map.of(
                "error", "Template processing misslyckades",
                "code", "TEMPLATE_PROCESSING_ERROR",
                "message", message
            ))
            .build();
    }
}