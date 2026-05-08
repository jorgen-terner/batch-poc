package infrastruktur.batch.client;

/**
 * Thrown when op-proxy-app returns a non-2xx HTTP status.
 */
public class ApiException extends RuntimeException {
    private final int statusCode;

    public ApiException(int statusCode, String message) {
        super("HTTP " + statusCode + ": " + message);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
