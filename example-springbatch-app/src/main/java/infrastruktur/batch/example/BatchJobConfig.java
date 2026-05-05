package infrastruktur.batch.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

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
        @Value("${app.sleep-seconds:10}") long sleepSeconds,
        @Value("${app.extra:1}") int extra
    ) {
        return new StepBuilder("sleepStep", jobRepository)
            .tasklet((contribution, chunkContext) -> {
                long effectiveSleep = Math.max(0, sleepSeconds);
                int repeatCount = Math.max(0, extra);
                LOG.info("Spring Batch example job started. Will sleep {} time(s), {} second(s) each time.", repeatCount, effectiveSleep);
                for (int i = 1; i <= repeatCount; i++) {
                    LOG.info("Sleep round {}/{} started.", i, repeatCount);
                    Thread.sleep(effectiveSleep * 1000L);
                    LOG.info("Sleep round {}/{} finished.", i, repeatCount);
                }
                LOG.info("Spring Batch example job finished after {} sleep round(s).", repeatCount);
                return RepeatStatus.FINISHED;
            }, transactionManager)
            .build();
    }
}
