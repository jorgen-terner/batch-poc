package infrastruktur.batch.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;

@Configuration
public class BatchJobConfig {

    private static final Logger LOG = LoggerFactory.getLogger(BatchJobConfig.class);

    @Bean
    public Job sleepJob(JobRepository jobRepository, Step sleepStep) {
        return new JobBuilder("sleepJob", jobRepository)
            .start(sleepStep)
            .build();
    }

    @Bean
    public Step sleepStep(
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager,
        StopSignalState stopSignalState,
        @Value("${app.sleep-seconds:10}") long sleepSeconds,
        @Value("${app.extra:1}") int extra
    ) {
        return new StepBuilder("sleepStep", jobRepository)
            .tasklet((contribution, chunkContext) -> {
                long effectiveSleep = Math.max(0, sleepSeconds);
                int repeatCount = Math.max(0, extra);
                LOG.info("Spring Batch example job started. Will sleep {} time(s), {} second(s) each time.", repeatCount, effectiveSleep);
                for (int i = 1; i <= repeatCount; i++) {
                    if (stopSignalState.isStopRequested() || Thread.currentThread().isInterrupted()) {
                        LOG.warn("Stop requested before sleep round {}/{}. Finishing early.", i, repeatCount);
                        contribution.setExitStatus(new ExitStatus("STOPPED", "Interrupted before sleep round"));
                        return RepeatStatus.FINISHED;
                    }
                    LOG.info("Sleep round {}/{} started.", i, repeatCount);
                    try {
                        sleepInterruptibly(Duration.ofSeconds(effectiveSleep), stopSignalState);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        LOG.warn("Stop requested during sleep round {}/{}. Finishing early.", i, repeatCount);
                        contribution.setExitStatus(new ExitStatus("STOPPED", "Interrupted during sleep round"));
                        return RepeatStatus.FINISHED;
                    }
                    if (stopSignalState.isStopRequested()) {
                        LOG.warn("Stop requested during sleep round {}/{}. Finishing early.", i, repeatCount);
                        contribution.setExitStatus(new ExitStatus("STOPPED", "Stop signal received during sleep round"));
                        return RepeatStatus.FINISHED;
                    }
                    LOG.info("Sleep round {}/{} finished.", i, repeatCount);
                }
                LOG.info("Spring Batch example job finished after {} sleep round(s).", repeatCount);
                return RepeatStatus.FINISHED;
            }, transactionManager)
            .build();
    }

    private void sleepInterruptibly(Duration totalDuration, StopSignalState stopSignalState) throws InterruptedException {
        final long totalMillis = Math.max(0, totalDuration.toMillis());
        final long pollMillis = 200;
        long sleptMillis = 0;

        while (sleptMillis < totalMillis) {
            if (stopSignalState.isStopRequested()) {
                return;
            }

            long remaining = totalMillis - sleptMillis;
            long nextSleep = Math.min(pollMillis, remaining);
            Thread.sleep(nextSleep);
            sleptMillis += nextSleep;
        }
    }
}
