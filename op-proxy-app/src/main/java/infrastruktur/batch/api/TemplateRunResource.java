package infrastruktur.batch.api;

import infrastruktur.batch.model.CancelRunRequestVO;
import infrastruktur.batch.model.CreateRunRequestVO;
import infrastruktur.batch.model.RunActionResponseVO;
import infrastruktur.batch.model.RunStatusResponseVO;
import infrastruktur.batch.service.TemplateRunService;
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
public class TemplateRunResource {
    private final TemplateRunService templateRunService;
    private final String namespace;

    @Inject
    public TemplateRunResource(
        TemplateRunService templateRunService,
        @ConfigProperty(name = "batch.job.namespace", defaultValue = "default") String namespace
    ) {
        this.templateRunService = templateRunService;
        this.namespace = namespace;
    }

    @POST
    @Path("api/v2/templates/{templateName}/runs")
    public RunActionResponseVO createRun(
        @jakarta.ws.rs.PathParam("templateName") String templateName,
        CreateRunRequestVO request
    ) {
        return templateRunService.createRun(namespace, templateName, request);
    }

    @GET
    @Path("api/v2/runs/{runName}")
    public RunStatusResponseVO status(@jakarta.ws.rs.PathParam("runName") String runName) {
        return templateRunService.status(namespace, runName);
    }

    @POST
    @Path("api/v2/runs/{runName}/cancel")
    public RunActionResponseVO cancel(
        @jakarta.ws.rs.PathParam("runName") String runName,
        CancelRunRequestVO request
    ) {
        return templateRunService.cancel(namespace, runName, request);
    }

}
