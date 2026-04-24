package infrastruktur.batch.service;

import infrastruktur.batch.model.JobParameterVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Shared stateless helpers used by both {@link JobControlService} and {@link TemplateExecutionService}.
 */
final class JobHelper {

    private static final Logger LOG = LoggerFactory.getLogger(JobHelper.class);

    private JobHelper() {}

    static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            LOG.warn("Failed to parse timestamp from Kubernetes status: {}", value);
            return null;
        }
    }

    static Long computeElapsedSeconds(Instant start, Instant completion) {
        if (start == null) {
            return null;
        }
        Instant end = completion == null ? Instant.now() : completion;
        return Duration.between(start, end).toSeconds();
    }

    static void validateTimeoutSeconds(Long timeoutSeconds) {
        if (timeoutSeconds != null && timeoutSeconds < 1) {
            throw new IllegalArgumentException("timeoutSeconds must be >= 1 when provided");
        }
    }

    static Map<String, String> normalizeParameters(List<JobParameterVO> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return Map.of();
        }

        Map<String, String> normalized = new LinkedHashMap<>();
        Set<String> duplicateGuard = new HashSet<>();

        for (int i = 0; i < parameters.size(); i++) {
            JobParameterVO parameter = parameters.get(i);
            if (parameter == null) {
                throw new IllegalArgumentException("parameters[" + i + "] must not be null");
            }
            String rawName = parameter.name();
            String rawValue = parameter.value();
            if (rawName == null || rawName.isBlank()) {
                throw new IllegalArgumentException("parameters[" + i + "].name must not be blank");
            }
            if (rawValue == null) {
                throw new IllegalArgumentException("parameters[" + i + "].value must not be null");
            }
            String name = rawName.trim();
            if (!duplicateGuard.add(name)) {
                throw new IllegalArgumentException("Duplicate parameter name: " + name);
            }
            normalized.put(name, rawValue);
        }

        return Map.copyOf(normalized);
    }
}
