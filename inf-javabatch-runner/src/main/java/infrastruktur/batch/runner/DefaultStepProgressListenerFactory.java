package infrastruktur.batch.runner;

import infrastruktur.batch.contract.StepProgressListener;
import infrastruktur.batch.contract.StepProgressListenerFactory;

public class DefaultStepProgressListenerFactory implements StepProgressListenerFactory {
    @Override
    public StepProgressListener create(String... stepNames) {
        return new DefaultStepProgressListener(stepNames);
    }
}
