package infrastruktur.batch.contract;

public interface StepProgressListenerFactory {
    StepProgressListener create(String... stepNames);
}
