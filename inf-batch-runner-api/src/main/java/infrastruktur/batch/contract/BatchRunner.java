package infrastruktur.batch.contract;

public interface BatchRunner {
    int runToCompletionFromEnvironment() throws InterruptedException;
}
