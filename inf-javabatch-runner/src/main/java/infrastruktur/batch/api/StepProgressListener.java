package infrastruktur.batch.api;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;

public interface StepProgressListener extends JobExecutionListener, StepExecutionListener {
    @Override
    void beforeJob(JobExecution jobExecution);

    @Override
    void beforeStep(StepExecution stepExecution);

    @Override
    ExitStatus afterStep(StepExecution stepExecution);

    @Override
    void afterJob(JobExecution jobExecution);
}
