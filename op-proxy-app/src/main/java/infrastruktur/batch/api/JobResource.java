package infrastruktur.batch.api;

import infrastruktur.batch.model.ActionResponse;
import infrastruktur.batch.model.RestartJobRequestVO;
import infrastruktur.batch.model.StartJobRequestVO;
import infrastruktur.batch.model.JobStatusResponse;
import infrastruktur.batch.service.JobControlService;
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
public class JobResource {
    private final JobControlService jobControlService;
    private final String namespace;

    @Inject
    public JobResource(
        JobControlService jobControlService,
        @ConfigProperty(name = "batch.job.namespace", defaultValue = "default") String namespace
    ) {
        this.jobControlService = jobControlService;
        this.namespace = namespace;
    }

    @GET
    @Path("health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @POST
    @Path("api/v1/jobs/{jobName}/start")
    public ActionResponse start(
        @jakarta.ws.rs.PathParam("jobName") String jobName,
        StartJobRequestVO request
    ) {
        return jobControlService.start(namespace, jobName, request);
    }

    @POST
    @Path("api/v1/jobs/{jobName}/stop")
    public ActionResponse stop(@jakarta.ws.rs.PathParam("jobName") String jobName) {
        return jobControlService.stop(namespace, jobName);
    }

    @POST
    @Path("api/v1/jobs/{jobName}/restart")
    public ActionResponse restart(
        @jakarta.ws.rs.PathParam("jobName") String jobName,
        RestartJobRequestVO request
    ) {
        return jobControlService.restart(namespace, jobName, request);
    }

    @GET
    @Path("api/v1/jobs/{jobName}/status")
    public JobStatusResponse status(@jakarta.ws.rs.PathParam("jobName") String jobName) {
        return jobControlService.status(namespace, jobName);
    }

}
