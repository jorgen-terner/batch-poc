package test.inv.batch.runner;

import infrastruktur.batch.contract.StepProgressListener;
import infrastruktur.batch.contract.StepProgressListenerFactory;

import java.util.ServiceLoader;

public final class StepProgressListenerProvider {
    private StepProgressListenerProvider() {
    }

    public static StepProgressListener create(String... stepNames) {
        ServiceLoader<StepProgressListenerFactory> loader = ServiceLoader.load(StepProgressListenerFactory.class);

        for (StepProgressListenerFactory factory : loader) {
            return factory.create(stepNames);
        }

        throw new IllegalStateException(
                "No StepProgressListener implementation found on classpath. " +
                        "Expected external runner jar in /opt/inf-javabatch-runner or on classpath."
        );
    }
}
