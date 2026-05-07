package infrastruktur.batch.service;

import infrastruktur.batch.model.JobParameterVO;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobHelperTest {

    // --- parseInstant ---

    @Test
    void parseInstantWithValidIsoStringShouldReturnInstant() {
        Instant result = JobHelper.parseInstant("2025-05-01T12:00:00Z");
        assertEquals(Instant.parse("2025-05-01T12:00:00Z"), result);
    }

    @Test
    void parseInstantWithNullShouldReturnNull() {
        assertNull(JobHelper.parseInstant(null));
    }

    @Test
    void parseInstantWithBlankStringShouldReturnNull() {
        assertNull(JobHelper.parseInstant("   "));
    }

    @Test
    void parseInstantWithInvalidFormatShouldReturnNull() {
        assertNull(JobHelper.parseInstant("not-a-timestamp"));
    }

    // --- computeElapsedSeconds ---

    @Test
    void computeElapsedSecondsWithNullStartShouldReturnNull() {
        assertNull(JobHelper.computeElapsedSeconds(null, Instant.now()));
    }

    @Test
    void computeElapsedSecondsWithStartAndCompletionShouldReturnDifference() {
        Instant start = Instant.parse("2025-01-01T10:00:00Z");
        Instant completion = Instant.parse("2025-01-01T10:05:00Z");
        Long elapsed = JobHelper.computeElapsedSeconds(start, completion);
        assertEquals(300L, elapsed);
    }

    @Test
    void computeElapsedSecondsWithNullCompletionShouldReturnPositiveValue() {
        Instant start = Instant.now().minusSeconds(60);
        Long elapsed = JobHelper.computeElapsedSeconds(start, null);
        assertTrue(elapsed >= 59, "Elapsed should be at least ~60 seconds");
    }

    // --- validateTimeoutSeconds ---

    @Test
    void validateTimeoutSecondsWithNullShouldPass() {
        JobHelper.validateTimeoutSeconds(null); // no exception expected
    }

    @Test
    void validateTimeoutSecondsWithPositiveValueShouldPass() {
        JobHelper.validateTimeoutSeconds(1L);
        JobHelper.validateTimeoutSeconds(3600L);
    }

    @Test
    void validateTimeoutSecondsWithZeroShouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> JobHelper.validateTimeoutSeconds(0L));
    }

    @Test
    void validateTimeoutSecondsWithNegativeValueShouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> JobHelper.validateTimeoutSeconds(-10L));
    }

    // --- normalizeParameters ---

    @Test
    void normalizeParametersWithNullShouldReturnEmptyMap() {
        Map<String, String> result = JobHelper.normalizeParameters(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void normalizeParametersWithEmptyListShouldReturnEmptyMap() {
        Map<String, String> result = JobHelper.normalizeParameters(List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void normalizeParametersWithValidEntriesShouldReturnNormalizedMap() {
        List<JobParameterVO> params = List.of(
            new JobParameterVO("  FILE  ", "/data/1.csv"),
            new JobParameterVO("ID", "42")
        );
        Map<String, String> result = JobHelper.normalizeParameters(params);
        assertEquals(2, result.size());
        assertEquals("/data/1.csv", result.get("FILE"));
        assertEquals("42", result.get("ID"));
    }

    @Test
    void normalizeParametersWithNullEntryShouldThrow() {
        List<JobParameterVO> params = new java.util.ArrayList<>();
        params.add(null);
        assertThrows(IllegalArgumentException.class, () -> JobHelper.normalizeParameters(params));
    }

    @Test
    void normalizeParametersWithBlankNameShouldThrow() {
        List<JobParameterVO> params = List.of(new JobParameterVO("  ", "value"));
        assertThrows(IllegalArgumentException.class, () -> JobHelper.normalizeParameters(params));
    }

    @Test
    void normalizeParametersWithNullValueShouldThrow() {
        List<JobParameterVO> params = List.of(new JobParameterVO("NAME", null));
        assertThrows(IllegalArgumentException.class, () -> JobHelper.normalizeParameters(params));
    }

    @Test
    void normalizeParametersWithDuplicateNameShouldThrow() {
        List<JobParameterVO> params = List.of(
            new JobParameterVO("FILE", "a"),
            new JobParameterVO("FILE", "b")
        );
        assertThrows(IllegalArgumentException.class, () -> JobHelper.normalizeParameters(params));
    }

    @Test
    void normalizeParametersTrimsDuplicateDetectionOnName() {
        // "FILE" and "  FILE  " should be treated as duplicates after trim
        List<JobParameterVO> params = List.of(
            new JobParameterVO("FILE", "a"),
            new JobParameterVO("  FILE  ", "b")
        );
        assertThrows(IllegalArgumentException.class, () -> JobHelper.normalizeParameters(params));
    }

    @Test
    void normalizeParametersEmptyValueShouldBeAllowed() {
        List<JobParameterVO> params = List.of(new JobParameterVO("FLAG", ""));
        Map<String, String> result = JobHelper.normalizeParameters(params);
        assertEquals("", result.get("FLAG"));
    }
}
