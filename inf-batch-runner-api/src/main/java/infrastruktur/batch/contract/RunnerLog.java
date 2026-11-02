package infrastruktur.batch.contract;

public final class RunnerLog {
    private RunnerLog() {
    }

    public static void info(String message) {
        System.out.println("[inf-javabatch-runner] INFO  " + message);
    }

    public static void warn(String message) {
        System.out.println("[inf-javabatch-runner] WARN  " + message);
    }

    public static void warn(String message, Throwable throwable) {
        warn(message + ". " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
    }

    public static void error(String message) {
        System.err.println("[inf-javabatch-runner] ERROR " + message);
    }

    public static void error(String message, Throwable throwable) {
        error(message + ". " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
    }
}
