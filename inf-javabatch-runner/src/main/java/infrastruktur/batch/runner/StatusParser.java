package infrastruktur.batch.runner;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class StatusParser {
    private static final Pattern JSON_STATUS_PATTERN = Pattern.compile(
            "\"status\"\\s*:\\s*\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE
    );

    static final Set<String> BATCH_STATUS_VALUES = Set.of(
            "STARTING",
            "STARTED",
            "STOPPING",
            "STOPPED",
            "COMPLETED",
            "FAILED",
            "ABANDONED",
            "UNKNOWN"
    );

    private StatusParser() {
    }

    static Optional<String> parseBatchStatus(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return Optional.empty();
        }

        String upper = normalize(rawResponse);
        if (BATCH_STATUS_VALUES.contains(upper)) {
            return Optional.of(upper);
        }

        Matcher matcher = JSON_STATUS_PATTERN.matcher(rawResponse);
        if (matcher.find()) {
            String parsed = normalize(matcher.group(1));
            if (BATCH_STATUS_VALUES.contains(parsed)) {
                return Optional.of(parsed);
            }
        }

        String[] tokens = upper.split("[^A-Z_]+");
        for (String token : tokens) {
            if (BATCH_STATUS_VALUES.contains(token)) {
                return Optional.of(token);
            }
        }
        return Optional.empty();
    }

    static String normalize(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        return normalized.toUpperCase(Locale.ROOT);
    }
}
