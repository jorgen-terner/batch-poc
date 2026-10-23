package infrastruktur.batch.runner;

final class RunnerLog {
    private RunnerLog() {
    }

    static void info(String message) {
        System.out.println("[inf-javabatch-runner] INFO  " + message);
    }

    static void warn(String message) {
        System.out.println("[inf-javabatch-runner] WARN  " + message);
    }

    static void warn(String message, Throwable throwable) {
        warn(message + ". " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
    }

    static void error(String message) {
        System.err.println("[inf-javabatch-runner] ERROR " + message);
    }

    static void error(String message, Throwable throwable) {
        error(message + ". " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
    }
}
