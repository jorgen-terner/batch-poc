package infrastruktur.batch.client;

/**
 * Kastas när op-proxy-app returnerar en HTTP-status utanför 2xx.
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
