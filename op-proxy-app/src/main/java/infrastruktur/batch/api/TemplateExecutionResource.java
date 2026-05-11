package infrastruktur.batch.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import infrastruktur.batch.model.ExecutionActionResponseVO;
import infrastruktur.batch.model.ExecutionLogsResponseVO;
import infrastruktur.batch.model.ExecutionStatusResponseVO;
import infrastruktur.batch.model.ExecutionStreamEventVO;
import infrastruktur.batch.model.StartExecutionRequestVO;
import infrastruktur.batch.service.TemplateExecutionService;
import io.smallrye.mutiny.Multi;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.RestSseElementType;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
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
        @QueryParam("namespace") String overrideNamespace,
        String rawRequestBody
    ) {
        StartExecutionRequestVO request = deserializeStartRequest(rawRequestBody);
        String effectiveNamespace = overrideNamespace != null && !overrideNamespace.isBlank() ? overrideNamespace : namespace;
        return templateExecutionService.start(effectiveNamespace, templateName, request);
    }

    private StartExecutionRequestVO deserializeStartRequest(String rawRequestBody) {
        if (rawRequestBody == null || rawRequestBody.isBlank()) {
            return null;  // Tom request body är tillåten för start; alla fält är valfria
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
    public ExecutionStatusResponseVO status(
        @jakarta.ws.rs.PathParam("executionName") String executionName,
        @QueryParam("namespace") String overrideNamespace
    ) {
        String effectiveNamespace = overrideNamespace != null && !overrideNamespace.isBlank() ? overrideNamespace : namespace;
        return templateExecutionService.status(effectiveNamespace, executionName);
    }

    @GET
    @Path("api/executions/{executionName}/logs")
    public ExecutionLogsResponseVO logs(
        @jakarta.ws.rs.PathParam("executionName") String executionName,
        @QueryParam("tailLines") Integer tailLines,
        @QueryParam("namespace") String overrideNamespace
    ) {
        String effectiveNamespace = overrideNamespace != null && !overrideNamespace.isBlank() ? overrideNamespace : namespace;
        return templateExecutionService.logs(effectiveNamespace, executionName, tailLines);
    }

    @GET
    @Path("api/executions/{executionName}/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestSseElementType(MediaType.APPLICATION_JSON)
    public Multi<ExecutionStreamEventVO> stream(
        @jakarta.ws.rs.PathParam("executionName") String executionName,
        @QueryParam("intervalSeconds") @DefaultValue("3") int intervalSeconds,
        @QueryParam("namespace") String overrideNamespace,
        @QueryParam("since") String since
    ) {
        String effectiveNamespace = overrideNamespace != null && !overrideNamespace.isBlank() ? overrideNamespace : namespace;
        return templateExecutionService.streamExecution(effectiveNamespace, executionName, intervalSeconds, since);
    }

    @POST
    @Path("api/executions/{executionName}/stop")
    public ExecutionActionResponseVO stop(
        @jakarta.ws.rs.PathParam("executionName") String executionName,
        @QueryParam("namespace") String overrideNamespace
    ) {
        String effectiveNamespace = overrideNamespace != null && !overrideNamespace.isBlank() ? overrideNamespace : namespace;
        return templateExecutionService.stop(effectiveNamespace, executionName);
    }
}


