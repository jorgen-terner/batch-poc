package infrastruktur.batch.api;

public interface BatchRunner {
    int runToCompletionFromEnvironment() throws InterruptedException;
}
