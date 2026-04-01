package infrastruktur.batch.service;

import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobCondition;
import io.fabric8.kubernetes.api.model.batch.v1.JobStatus;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class JobPhaseResolver {
    public String resolvePhase(Job job, int active, int succeeded, int failed, boolean suspended) {
        if (suspended && (job.getStatus() == null || job.getStatus().getStartTime() == null)) {
            return "SUSPENDED";
        }
        if (active > 0) {
            return "RUNNING";
        }
        List<JobCondition> conditions = job.getStatus() == null ? null : job.getStatus().getConditions();
        if (conditions != null) {
            for (JobCondition condition : conditions) {
                if ("Complete".equalsIgnoreCase(condition.getType()) && "True".equalsIgnoreCase(condition.getStatus())) {
                    return "SUCCEEDED";
                }
                if ("Failed".equalsIgnoreCase(condition.getType()) && "True".equalsIgnoreCase(condition.getStatus())) {
                    return "FAILED";
                }
            }
        }
        if (succeeded > 0) {
            return "SUCCEEDED";
        }
        if (failed > 0) {
            return "FAILED";
        }
        return suspended ? "SUSPENDED" : "PENDING";
    }

    public String resolvePhase(Job job) {
        JobStatus status = job.getStatus();
        int active = status != null && status.getActive() != null ? status.getActive() : 0;
        int succeeded = status != null && status.getSucceeded() != null ? status.getSucceeded() : 0;
        int failed = status != null && status.getFailed() != null ? status.getFailed() : 0;
        boolean suspended = job.getSpec() != null && Boolean.TRUE.equals(job.getSpec().getSuspend());
        return resolvePhase(job, active, succeeded, failed, suspended);
    }

    public boolean isTerminalPhase(String phase) {
        String normalized = normalize(phase);
        return "SUCCEEDED".equals(normalized) || "FAILED".equals(normalized);
    }

    public String normalize(String value) {
        return value == null ? "UNKNOWN" : value.trim().toUpperCase();
    }
}
