package infrastruktur.batch.service;

import infrastruktur.batch.model.ActionResponse;
import infrastruktur.batch.model.JobStatusResponse;
import infrastruktur.batch.model.RestartJobRequestVO;
import infrastruktur.batch.model.StartJobRequestVO;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;

@ApplicationScoped
public class JobControlService {
    private static final Logger LOG = LoggerFactory.getLogger(JobControlService.class);
    private static final long DEFAULT_DELETION_POLL_INTERVAL_MILLIS = 1000L;
    private static final int DEFAULT_DELETION_MAX_ATTEMPTS = 30;

    private final KubernetesJobGateway kubernetesJobGateway;
    private final JobPhaseResolver jobPhaseResolver;
    private final long deletionPollIntervalMillis;
    private final int deletionMaxAttempts;

    @Inject
    public JobControlService(
        KubernetesJobGateway kubernetesJobGateway,
        JobPhaseResolver jobPhaseResolver,
        @ConfigProperty(name = "batch.job.deletion.poll-interval-millis", defaultValue = "1000") long deletionPollIntervalMillis,
        @ConfigProperty(name = "batch.job.deletion.max-attempts", defaultValue = "30") int deletionMaxAttempts
    ) {
        this.kubernetesJobGateway = kubernetesJobGateway;
        this.jobPhaseResolver = jobPhaseResolver;
        if (deletionPollIntervalMillis < 1) {
            throw new IllegalArgumentException("batch.job.deletion.poll-interval-millis must be >= 1");
        }
        if (deletionMaxAttempts < 1) {
            throw new IllegalArgumentException("batch.job.deletion.max-attempts must be >= 1");
        }
        this.deletionPollIntervalMillis = deletionPollIntervalMillis;
        this.deletionMaxAttempts = deletionMaxAttempts;
    }

    public JobControlService(KubernetesClient client) {
        this(
            new KubernetesJobGateway(client),
            new JobPhaseResolver(),
            DEFAULT_DELETION_POLL_INTERVAL_MILLIS,
            DEFAULT_DELETION_MAX_ATTEMPTS
        );
    }

    public ActionResponse start(String namespace, String jobName, StartJobRequestVO request) {
        Long timeoutSeconds = request == null ? null : request.timeoutSeconds();
        Map<String, String> parameters = JobHelper.normalizeParameters(request == null ? null : request.parameters());

        JobHelper.validateTimeoutSeconds(timeoutSeconds);
        if (parameters.isEmpty()) {
            kubernetesJobGateway.requireJob(namespace, jobName);
            kubernetesJobGateway.patchStartConfiguration(namespace, jobName, timeoutSeconds);
            LOG.info("Started job {}/{} by unsuspending (timeoutSeconds={})", namespace, jobName, timeoutSeconds);
            return action(namespace, jobName, "start", "RUNNING", "Job unsuspended");
        }

        Job existing = kubernetesJobGateway.requireJob(namespace, jobName);
        kubernetesJobGateway.patchSuspend(namespace, jobName, true);
        int deletedPods = kubernetesJobGateway.deleteActivePods(namespace, jobName);
        kubernetesJobGateway.deleteJobPreservingPods(namespace, jobName);
        kubernetesJobGateway.waitForDeletion(namespace, jobName, deletionPollIntervalMillis, deletionMaxAttempts);

        Job recreated = kubernetesJobGateway.createRestartedJob(existing, timeoutSeconds, parameters);
        kubernetesJobGateway.createJob(namespace, recreated);

        LOG.info(
            "Started job {}/{} by recreate (deletedActivePods={}, timeoutSeconds={}, parameters={})",
            namespace,
            jobName,
            deletedPods,
            timeoutSeconds,
            parameters.keySet()
        );
        return action(namespace, jobName, "start", "RUNNING", "Job recreated and started");
    }

    public ActionResponse stop(String namespace, String jobName) {
        kubernetesJobGateway.requireJob(namespace, jobName);
        kubernetesJobGateway.patchSuspend(namespace, jobName, true);
        int deletedPods = kubernetesJobGateway.deleteActivePods(namespace, jobName);

        LOG.info("Stopped job {}/{} and deleted {} active pod(s)", namespace, jobName, deletedPods);
        return action(namespace, jobName, "stop", "SUSPENDED", "Job suspended and active pods deleted: " + deletedPods);
    }

    public ActionResponse restart(String namespace, String jobName, RestartJobRequestVO request) {
        Long timeoutSeconds = request == null ? null : request.timeoutSeconds();
        boolean keepFailedPods = request == null || request.keepFailedPods() == null || request.keepFailedPods();
        Map<String, String> parameters = JobHelper.normalizeParameters(request == null ? null : request.parameters());

        JobHelper.validateTimeoutSeconds(timeoutSeconds);
        Job existing = kubernetesJobGateway.requireJob(namespace, jobName);
        kubernetesJobGateway.patchSuspend(namespace, jobName, true);
        int deletedPods = keepFailedPods
            ? kubernetesJobGateway.deleteActivePods(namespace, jobName)
            : kubernetesJobGateway.deletePods(namespace, jobName);

        if (keepFailedPods) {
            kubernetesJobGateway.deleteJobPreservingPods(namespace, jobName);
        } else {
            kubernetesJobGateway.deleteJob(namespace, jobName);
        }
        kubernetesJobGateway.waitForDeletion(namespace, jobName, deletionPollIntervalMillis, deletionMaxAttempts);

        Job recreated = kubernetesJobGateway.createRestartedJob(existing, timeoutSeconds, parameters);
        kubernetesJobGateway.createJob(namespace, recreated);

        LOG.info(
            "Restarted job {}/{} by delete/recreate. Deleted {} pod(s), timeoutSeconds={}, keepFailedPods={}, parameters={}",
            namespace,
            jobName,
            deletedPods,
            timeoutSeconds,
            keepFailedPods,
            parameters.keySet()
        );
        return action(namespace, jobName, "restart", "RUNNING", "Job recreated and started");
    }

    public JobStatusResponse status(String namespace, String jobName) {
        Job job = kubernetesJobGateway.requireJob(namespace, jobName);
        JobStatus status = job.getStatus();

        int active = status != null && status.getActive() != null ? status.getActive() : 0;
        int succeeded = status != null && status.getSucceeded() != null ? status.getSucceeded() : 0;
        int failed = status != null && status.getFailed() != null ? status.getFailed() : 0;
        boolean suspended = job.getSpec() != null && Boolean.TRUE.equals(job.getSpec().getSuspend());

        Instant startTime = JobHelper.parseInstant(status == null ? null : status.getStartTime());
        Instant completionTime = JobHelper.parseInstant(status == null ? null : status.getCompletionTime());
        Long elapsed = JobHelper.computeElapsedSeconds(startTime, completionTime);

        String phase = jobPhaseResolver.resolvePhase(job, active, succeeded, failed, suspended);

        return new JobStatusResponse(
            namespace,
            jobName,
            phase,
            suspended,
            active,
            succeeded,
            failed,
            startTime,
            completionTime,
            elapsed
        );
    }

    private ActionResponse action(String namespace, String jobName, String action, String state, String message) {
        return new ActionResponse(namespace, jobName, action, state, message, Instant.now());
    }


}

