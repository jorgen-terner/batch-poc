package com.example.triggerjobapi.controller;

import com.example.triggerjobapi.dto.JobStartRequest;
import com.example.triggerjobapi.dto.JobStartResponse;
import com.example.triggerjobapi.dto.JobStatusResponse;
import com.example.triggerjobapi.exception.JobException;
import com.example.triggerjobapi.service.KubernetesJobService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

@Path("/api/jobs")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class JobController {

    @Inject
    KubernetesJobService jobService;

    /**
     * Starta ett nytt Job
     */
    @POST
    public Response startJob(@Valid JobStartRequest request) {
        try {
            String jobId = jobService.startJob(request);
            JobStartResponse response = JobStartResponse.builder()
                .jobId(jobId)
                .jobName(request.getJobName())
                .message("Job startad framgångsrikt")
                .success(true)
                .build();
            return Response.status(Response.Status.CREATED).entity(response).build();
        } catch (JobException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(JobStartResponse.builder()
                    .message(e.getMessage())
                    .success(false)
                    .build())
                .build();
        }
    }

    /**
     * Hämta status för ett Job
     */
    @GET
    @Path("/{jobId}")
    public Response getJobStatus(@PathParam("jobId") String jobId) {
        try {
            JobStatusResponse response = jobService.getJobStatus(jobId);
            return Response.ok(response).build();
        } catch (JobException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }

    /**
     * Lista alla Jobs
     */
    @GET
    public Response listJobs() {
        List<JobStatusResponse> jobs = jobService.listJobs();
        return Response.ok(jobs).build();
    }

    /**
     * Ta bort/stoppa ett Job
     */
    @DELETE
    @Path("/{jobId}")
    public Response deleteJob(@PathParam("jobId") String jobId) {
        try {
            jobService.deleteJob(jobId);
            return Response.ok(Map.of("message", "Job raderat framgångsrikt")).build();
        } catch (JobException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }
}
