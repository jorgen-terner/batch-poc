package infrastruktur.batch.contract;

public final class BatchRunnerProvider {
    private static final String DEFAULT_RUNNER_CLASS =
            "infrastruktur.batch.runner.DefaultBatchRunner";

    private BatchRunnerProvider() {
    }

    public static BatchRunner get() {
        BatchRunner runner = ServiceProviderSupport.resolveProvider(
                BatchRunner.class,
                DEFAULT_RUNNER_CLASS,
                BatchRunnerProvider.class.getClassLoader());
        if (runner != null) {
            return runner;
        }

        throw new IllegalStateException(
                "No BatchRunner implementation found on classpath. " +
                        "Expected external runner jar in /opt/inf-javabatch-runner or on classpath."
        );
    }
}
