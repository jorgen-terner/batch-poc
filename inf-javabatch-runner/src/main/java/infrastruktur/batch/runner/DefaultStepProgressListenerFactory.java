package infrastruktur.batch.runner;

import infrastruktur.batch.contract.StepProgressListener;
import infrastruktur.batch.contract.StepProgressListenerFactory;
import org.springframework.batch.core.configuration.JobRegistry;

public class DefaultStepProgressListenerFactory implements StepProgressListenerFactory {
    @Override
    public StepProgressListener create(JobRegistry jobRegistry) {
        return new DefaultStepProgressListener(jobRegistry);
    }
}
