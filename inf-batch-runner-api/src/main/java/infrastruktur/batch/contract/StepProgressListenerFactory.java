package infrastruktur.batch.contract;

import org.springframework.batch.core.configuration.JobRegistry;

public interface StepProgressListenerFactory {
    StepProgressListener create(JobRegistry jobRegistry);
}
