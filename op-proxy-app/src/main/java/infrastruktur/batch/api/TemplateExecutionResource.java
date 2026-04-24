package infrastruktur.batch.api;

import infrastruktur.batch.model.ExecutionActionResponseVO;
import infrastruktur.batch.model.ExecutionStatusResponseVO;
import infrastruktur.batch.model.StartExecutionRequestVO;
import infrastruktur.batch.model.StopExecutionRequestVO;
import infrastruktur.batch.service.TemplateExecutionService;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TemplateExecutionResource {
    private final TemplateExecutionService templateExecutionService;
    private final String namespace;

    @Inject
    public TemplateExecutionResource(
        TemplateExecutionService templateExecutionService,
        @ConfigProperty(name = "batch.job.namespace", defaultValue = "default") String namespace
    ) {
        this.templateExecutionService = templateExecutionService;
        this.namespace = namespace;
    }

    @POST
    @Path("api/v2/templates/{templateName}/start")
    public ExecutionActionResponseVO start(
        @jakarta.ws.rs.PathParam("templateName") String templateName,
        StartExecutionRequestVO request
    ) {
        return templateExecutionService.start(namespace, templateName, request);
    }

    @GET
    @Path("api/v2/executions/{executionName}")
    public ExecutionStatusResponseVO status(@jakarta.ws.rs.PathParam("executionName") String executionName) {
        return templateExecutionService.status(namespace, executionName);
    }

    @POST
    @Path("api/v2/executions/{executionName}/stop")
    public ExecutionActionResponseVO stop(
        @jakarta.ws.rs.PathParam("executionName") String executionName,
        StopExecutionRequestVO request
    ) {
        return templateExecutionService.stop(namespace, executionName, request);
    }
}
