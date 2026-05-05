package infrastruktur.batch.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import infrastruktur.batch.model.ExecutionActionResponseVO;
import infrastruktur.batch.model.ExecutionStatusResponseVO;
import infrastruktur.batch.model.StartExecutionRequestVO;
import infrastruktur.batch.service.TemplateExecutionService;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TemplateExecutionResource {
    private final TemplateExecutionService templateExecutionService;
    private final ObjectMapper objectMapper;
    private final String namespace;

    @Inject
    public TemplateExecutionResource(
        TemplateExecutionService templateExecutionService,
        ObjectMapper objectMapper,
        @ConfigProperty(name = "batch.job.namespace", defaultValue = "default") String namespace
    ) {
        this.templateExecutionService = templateExecutionService;
        this.objectMapper = objectMapper;
        this.namespace = namespace;
    }

    @GET
    @Path("health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @POST
    @Path("api/templates/{templateName}/start")
    public ExecutionActionResponseVO start(
        @jakarta.ws.rs.PathParam("templateName") String templateName,
        String rawRequestBody
    ) {
        StartExecutionRequestVO request = deserializeStartRequest(rawRequestBody);
        return templateExecutionService.start(namespace, templateName, request);
    }

    private StartExecutionRequestVO deserializeStartRequest(String rawRequestBody) {
        if (rawRequestBody == null || rawRequestBody.isBlank()) {
            return null;  // Empty body is OK for start; all fields are optional
        }
        try {
            return objectMapper.readValue(rawRequestBody, StartExecutionRequestVO.class);
        } catch (JsonProcessingException exception) {
            String message = JsonErrorHelper.extractJsonErrorMessage(exception);
            throw new IllegalArgumentException(message, exception);
        }
    }

    @GET
    @Path("api/executions/{executionName}")
    public ExecutionStatusResponseVO status(@jakarta.ws.rs.PathParam("executionName") String executionName) {
        return templateExecutionService.status(namespace, executionName);
    }

    @POST
    @Path("api/executions/{executionName}/stop")
    public ExecutionActionResponseVO stop(@jakarta.ws.rs.PathParam("executionName") String executionName) {
        return templateExecutionService.stop(namespace, executionName);
    }
}


