package com.batchjobapp.api;

import com.batchjobapp.model.ActionResponse;
import com.batchjobapp.model.JobMetricsResponse;
import com.batchjobapp.model.JobReportRequest;
import com.batchjobapp.model.JobStatusResponse;
import com.batchjobapp.service.JobControlService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class JobResource {
    private final JobControlService jobControlService;

    @Inject
    public JobResource(JobControlService jobControlService) {
        this.jobControlService = jobControlService;
    }

    @GET
    @Path("health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @POST
    @Path("api/v1/jobs/{namespace}/{jobName}/start")
    public JobStatusResponse start(
        @PathParam("namespace") String namespace,
        @PathParam("jobName") String jobName,
        @QueryParam("intervalSeconds") @DefaultValue("5") long intervalSeconds,
        @QueryParam("timeoutSeconds") Long timeoutSeconds
    ) {
        return jobControlService.startAndWait(namespace, jobName, intervalSeconds, timeoutSeconds);
    }

    @POST
    @Path("api/v1/jobs/{namespace}/{jobName}/stop")
    public ActionResponse stop(@PathParam("namespace") String namespace, @PathParam("jobName") String jobName) {
        return jobControlService.stop(namespace, jobName);
    }

    @POST
    @Path("api/v1/jobs/{namespace}/{jobName}/restart")
    public ActionResponse restart(@PathParam("namespace") String namespace, @PathParam("jobName") String jobName) {
        return jobControlService.restart(namespace, jobName);
    }

    @GET
    @Path("api/v1/jobs/{namespace}/{jobName}/status")
    public JobStatusResponse status(@PathParam("namespace") String namespace, @PathParam("jobName") String jobName) {
        return jobControlService.status(namespace, jobName);
    }

    @GET
    @Path("api/v1/jobs/{namespace}/{jobName}/metrics")
    public JobMetricsResponse metrics(@PathParam("namespace") String namespace, @PathParam("jobName") String jobName) {
        return jobControlService.metrics(namespace, jobName);
    }

    @POST
    @Path("api/v1/jobs/{namespace}/{jobName}/report")
    public ActionResponse report(
        @PathParam("namespace") String namespace,
        @PathParam("jobName") String jobName,
        JobReportRequest request
    ) {
        return jobControlService.report(namespace, jobName, request);
    }
}
