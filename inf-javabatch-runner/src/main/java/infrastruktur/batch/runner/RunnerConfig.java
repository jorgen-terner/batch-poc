package infrastruktur.batch.runner;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

record RunnerConfig(
        String startUrl,
        String statusUrl,
        String stopUrl,
        String execIdParamName,
        long httpTimeoutSeconds,
        long statusPollIntervalSeconds,
        long maxPollSeconds,
        long stopWaitSeconds,
        boolean failOpenOnStatusError,
        int maxStatusErrorRetries,
        int maxUnknownStatusRetries,
        Set<String> activeStatuses,
        Set<String> successStatuses,
        Set<String> failureStatuses,
        Map<String, String> commonHeaders,
        Map<String, String> startHeaders,
        Map<String, String> statusHeaders,
        Map<String, String> stopHeaders
) {
    static RunnerConfig fromEnvironment() {
        String startUrl = readRequired("START");
        String statusUrl = readRequired("STATUS");
        String stopUrl = readOptional("STOP");
        String execIdParamName = readOptional("EXEC_ID_PARAM_NAME", "execId");
        long httpTimeoutSeconds = readLong("HTTP_TIMEOUT_SECONDS", 30, 1);
        long statusPollIntervalSeconds = readLong("STATUS_POLL_INTERVAL_SECONDS", 5, 1);
        long maxPollSeconds = readLong("MAX_POLL_SECONDS", 3600, 0);
        long stopWaitSeconds = readLong("STOP_WAIT_SECONDS", 15, 0);
        boolean failOpenOnStatusError = readBoolean("FAIL_OPEN_ON_STATUS_ERROR", true);
        int maxStatusErrorRetries = readInt("MAX_STATUS_ERROR_RETRIES", 12, 0);
        int maxUnknownStatusRetries = readInt("MAX_UNKNOWN_STATUS_RETRIES", 12, 0);

        Set<String> activeStatuses = parseStatuses(
                readOptional("ACTIVE_STATUS_VALUES", "STARTING,STARTED,STOPPING"),
                "ACTIVE_STATUS_VALUES"
        );
        Set<String> successStatuses = parseStatuses(
                readOptional("SUCCESS_STATUS_VALUES", "COMPLETED"),
                "SUCCESS_STATUS_VALUES"
        );
        Set<String> failureStatuses = parseStatuses(
                readOptional("FAILURE_STATUS_VALUES", "FAILED,ABANDONED,UNKNOWN,STOPPED"),
                "FAILURE_STATUS_VALUES"
        );
        validateNoOverlap(activeStatuses, successStatuses, "ACTIVE_STATUS_VALUES", "SUCCESS_STATUS_VALUES");
        validateNoOverlap(activeStatuses, failureStatuses, "ACTIVE_STATUS_VALUES", "FAILURE_STATUS_VALUES");
        validateNoOverlap(successStatuses, failureStatuses, "SUCCESS_STATUS_VALUES", "FAILURE_STATUS_VALUES");

        Map<String, String> commonHeaders = parseHeaders(readOptional("COMMON_HEADERS"), "COMMON_HEADERS");
        Map<String, String> startHeaders = parseHeaders(readOptional("START_HEADERS"), "START_HEADERS");
        Map<String, String> statusHeaders = parseHeaders(readOptional("STATUS_HEADERS"), "STATUS_HEADERS");
        Map<String, String> stopHeaders = parseHeaders(readOptional("STOP_HEADERS"), "STOP_HEADERS");

        return new RunnerConfig(
                startUrl,
                statusUrl,
                stopUrl,
                execIdParamName,
                httpTimeoutSeconds,
                statusPollIntervalSeconds,
                maxPollSeconds,
                stopWaitSeconds,
                failOpenOnStatusError,
                maxStatusErrorRetries,
                maxUnknownStatusRetries,
                activeStatuses,
                successStatuses,
                failureStatuses,
                commonHeaders,
                startHeaders,
                statusHeaders,
                stopHeaders
        );
    }

    private static String readRequired(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required environment variable: " + name);
        }
        return value.trim();
    }

    private static String readOptional(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String readOptional(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static long readLong(String name, long defaultValue, long minValue) {
        String raw = readOptional(name);
        long value = raw == null ? defaultValue : Long.parseLong(raw.trim());
        if (value < minValue) {
            throw new IllegalArgumentException(name + " must be >= " + minValue);
        }
        return value;
    }

    private static int readInt(String name, int defaultValue, int minValue) {
        String raw = readOptional(name);
        int value = raw == null ? defaultValue : Integer.parseInt(raw.trim());
        if (value < minValue) {
            throw new IllegalArgumentException(name + " must be >= " + minValue);
        }
        return value;
    }

    private static boolean readBoolean(String name, boolean defaultValue) {
        String raw = readOptional(name);
        return raw == null ? defaultValue : Boolean.parseBoolean(raw.trim());
    }

    private static Set<String> parseStatuses(String raw, String propertyName) {
        LinkedHashSet<String> statuses = new LinkedHashSet<>();
        if (raw != null) {
            Arrays.stream(raw.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .map(StatusParser::normalize)
                    .forEach(statuses::add);
        }
        if (statuses.isEmpty()) {
            throw new IllegalArgumentException(propertyName + " must define at least one status value");
        }
        return Set.copyOf(statuses);
    }

    private static void validateNoOverlap(Set<String> first, Set<String> second, String firstName, String secondName) {
        LinkedHashSet<String> overlap = new LinkedHashSet<>(first);
        overlap.retainAll(second);
        if (!overlap.isEmpty()) {
            throw new IllegalArgumentException("Status sets overlap between " + firstName + " and " + secondName + ": " + overlap);
        }
    }

    private static Map<String, String> parseHeaders(String rawHeaders, String envName) {
        if (rawHeaders == null || rawHeaders.isBlank()) {
            return Map.of();
        }

        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        String[] entries = rawHeaders.split("[;\\r\\n]+");
        for (String entry : entries) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            int separatorIndex = trimmed.indexOf('=');
            if (separatorIndex < 0) {
                separatorIndex = trimmed.indexOf(':');
            }
            if (separatorIndex <= 0 || separatorIndex == trimmed.length() - 1) {
                throw new IllegalArgumentException(
                        envName + " contains invalid header entry: '" + trimmed + "'. Expected format 'Header=Value'"
                );
            }

            String name = trimmed.substring(0, separatorIndex).trim();
            String value = trimmed.substring(separatorIndex + 1).trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException(envName + " contains a blank header name");
            }
            if (value.isEmpty()) {
                throw new IllegalArgumentException(envName + " contains a blank value for header '" + name + "'");
            }
            headers.put(name, value);
        }

        return headers.isEmpty() ? Map.of() : Map.copyOf(headers);
    }
}
