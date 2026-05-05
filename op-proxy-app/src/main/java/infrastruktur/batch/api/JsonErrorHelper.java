package infrastruktur.batch.api;

import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * Utility for extracting and formatting deserialization error messages from
 * Jackson exceptions while avoiding sensitive data leakage.
 */
public class JsonErrorHelper {

    /**
     * Walk the exception cause chain and extract the first Jackson exception message.
     * Returns null if no Jackson exception is found.
     */
    public static String extractJsonErrorMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof JsonProcessingException jsonException) {
                String location = jsonException.getLocation() == null
                    ? ""
                    : " (line " + jsonException.getLocation().getLineNr()
                        + ", column " + jsonException.getLocation().getColumnNr() + ")";
                return "Request body could not be deserialized" + location + ": " + jsonException.getOriginalMessage();
            }
            current = current.getCause();
        }
        return null;
    }

    private JsonErrorHelper() {
        // utility class
    }
}
