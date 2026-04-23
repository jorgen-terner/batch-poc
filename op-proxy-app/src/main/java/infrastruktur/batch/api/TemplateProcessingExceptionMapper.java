package infrastruktur.batch.api;

import infrastruktur.batch.service.TemplateProcessingException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@Provider
public class TemplateProcessingExceptionMapper implements ExceptionMapper<TemplateProcessingException> {
    private static final Logger LOG = LoggerFactory.getLogger(TemplateProcessingExceptionMapper.class);

    @Override
    public Response toResponse(TemplateProcessingException exception) {
        LOG.warn("Template processing failed: {}", exception.getMessage());
        return Response.status(422)
            .entity(Map.of(
                "error", "Template processing failed",
                "code", "TEMPLATE_PROCESSING_ERROR",
                "message", exception.getMessage()
            ))
            .build();
    }
}