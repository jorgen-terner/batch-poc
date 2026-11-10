package infrastruktur.batch.contract;

import org.springframework.batch.core.configuration.JobRegistry;

public final class StepProgressListenerProvider {
    private static final String DEFAULT_FACTORY_CLASS =
            "infrastruktur.batch.runner.DefaultStepProgressListenerFactory";

    private StepProgressListenerProvider() {
    }

    public static StepProgressListener create(JobRegistry jobRegistry) {
        StepProgressListenerFactory factory = ServiceProviderSupport.resolveProvider(
                StepProgressListenerFactory.class,
                DEFAULT_FACTORY_CLASS,
                StepProgressListenerProvider.class.getClassLoader());
        if (factory != null) {
            return factory.create(jobRegistry);
        }

        throw new IllegalStateException(
                "No StepProgressListener implementation found on classpath. " +
                        "Expected external runner jar in /opt/inf-javabatch-runner or on classpath.");
    }
}
