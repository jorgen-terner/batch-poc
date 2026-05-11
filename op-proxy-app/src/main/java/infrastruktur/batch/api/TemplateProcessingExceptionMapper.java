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
 * Mappar TemplateProcessingException till 422 Unprocessable Entity.
 * Kastas när template processing misslyckas (template saknas, ogiltigt format o.s.v.).
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