package infrastruktur.batch.runner;

import infrastruktur.batch.contract.RunnerLog;
import infrastruktur.batch.contract.StepProgressListener;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.launch.NoSuchJobException;
import org.springframework.batch.core.step.StepLocator;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public class DefaultStepProgressListener implements StepProgressListener {
    private final JobRegistry jobRegistry;
    private final ConcurrentMap<Long, Integer> totalStepsByExecutionId = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, AtomicInteger> currentStepsByExecutionId = new ConcurrentHashMap<>();

    public DefaultStepProgressListener(JobRegistry jobRegistry) {
        this.jobRegistry = Objects.requireNonNull(jobRegistry, "jobRegistry must not be null");
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        long jobExecutionId = jobExecution.getId();
        int totalSteps = resolveTotalStepCount(jobExecution);
        totalStepsByExecutionId.put(jobExecutionId, totalSteps);
        currentStepsByExecutionId.put(jobExecutionId, new AtomicInteger(0));
        RunnerLog.info("BATCH JOB STARTED " + jobExecution.getJobInstance().getJobName() + " (total steps: " + totalSteps + ")");
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        long jobExecutionId = stepExecution.getJobExecution().getId();
        AtomicInteger counter = currentStepsByExecutionId.computeIfAbsent(jobExecutionId, ignored -> new AtomicInteger(0));
        int currentStep = counter.incrementAndGet();
        int totalSteps = totalStepsByExecutionId.getOrDefault(jobExecutionId, currentStep);
        RunnerLog.info("BATCH STEP STARTED [" + currentStep + "/" + totalSteps + "] " + stepExecution.getStepName());
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        long jobExecutionId = stepExecution.getJobExecution().getId();
        int totalSteps = totalStepsByExecutionId.getOrDefault(jobExecutionId, 0);
        AtomicInteger counter = currentStepsByExecutionId.get(jobExecutionId);
        int currentStep = counter != null ? counter.get() : 0;
        RunnerLog.info("BATCH STEP FINISHED [" + currentStep + "/" + totalSteps + "] " + stepExecution.getStepName() + " (" + stepExecution.getStatus() + ")");
        return stepExecution.getExitStatus();
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        long jobExecutionId = jobExecution.getId();
        totalStepsByExecutionId.remove(jobExecutionId);
        currentStepsByExecutionId.remove(jobExecutionId);
        RunnerLog.info("BATCH JOB FINISHED " + jobExecution.getJobInstance().getJobName() + " (status: " + jobExecution.getStatus() + ")");
    }

    private int resolveTotalStepCount(JobExecution jobExecution) {
        String jobName = jobExecution.getJobInstance().getJobName();
        try {
            Job job = jobRegistry.getJob(jobName);
            if (job instanceof StepLocator stepLocator) {
                return stepLocator.getStepNames().size();
            }

            RunnerLog.warn("Could not resolve total steps from JobRegistry for job " + jobName +
                    " because the job does not implement StepLocator");
        } catch (NoSuchJobException noSuchJobException) {
            RunnerLog.warn("Could not resolve total steps from JobRegistry for job " + jobName, noSuchJobException);
        }

        return jobExecution.getStepExecutions() != null ? jobExecution.getStepExecutions().size() : 0;
    }
}
