package infrastruktur.batch.api;

import infrastruktur.batch.runner.DefaultStepProgressListener;
import org.springframework.batch.core.configuration.JobRegistry;

public final class StepProgressListenerFactory {
    private StepProgressListenerFactory() {
    }

    public static StepProgressListener createDefault(JobRegistry jobRegistry) {
        return new DefaultStepProgressListener(jobRegistry);
    }
}
