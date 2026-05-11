package infrastruktur.batch.api;

import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * Hjälpklass för att extrahera och formatera deserialiseringsfelmeddelanden från
 * Jackson-undantag utan att läcka känslig data.
 */
public class JsonErrorHelper {

    /**
     * Traverserar undantagets orsakskedja och extraherar det första Jackson-felmeddelandet.
     * Returnerar null om inget Jackson-undantag hittas.
     */
    public static String extractJsonErrorMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof JsonProcessingException jsonException) {
                String location = jsonException.getLocation() == null
                    ? ""
                    : " (line " + jsonException.getLocation().getLineNr()
                        + ", column " + jsonException.getLocation().getColumnNr() + ")";
                return "Request body kunde inte deserialiseras" + location + ": " + jsonException.getOriginalMessage();
            }
            current = current.getCause();
        }
        return null;
    }

    private JsonErrorHelper() {
        // hjälpklass
    }
}
