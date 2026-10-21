package infrastruktur.batch.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for deduplication logic in TemplateExecutionService.removePrefixOverlap().
 * 
 * Tests cover:
 * - No overlap: returns full current chunk
 * - Full chunk overlap: detects and removes duplicate
 * - Partial overlap ending at line boundary: deduplicates safely
 * - Partial overlap NOT at line boundary: keeps full current chunk (conservative)
 * - Empty/null handling
 */
class RemovePrefixOverlapTest {

    @Test
    void noOverlapShouldReturnFullChunk() {
        String result = StreamLogDeduplicator.removePrefixOverlap("hello", "world");
        assertEquals("world", result);
    }

    @Test
    void fullChunkOverlapShouldRemoveDuplicate() {
        // Previous tail ends with "llo\n", current chunk starts with "llo\n"
        String result = StreamLogDeduplicator.removePrefixOverlap("he\nllo\n", "llo\n");
        assertEquals("", result);
    }

    @Test
    void partialOverlapAtLineBoundaryShouldDeduplicate() {
        // Previous tail ends with "line1\n", current chunk starts with "line1\n"
        String result = StreamLogDeduplicator.removePrefixOverlap("prefix\nline1\n", "line1\nline2\n");
        assertEquals("line2\n", result);
    }

    @Test
    void partialOverlapNotAtLineBoundaryShouldNotDeduplicate() {
        // Previous tail ends with "hel", current chunk starts with "hel" but NOT at line boundary
        String result = StreamLogDeduplicator.removePrefixOverlap("hel", "hello");
        assertEquals("hello", result, "Conservative dedup: should not remove partial overlap that doesn't end at line boundary");
    }

    @Test
    void overlapAtCarriageReturnBoundaryShouldDeduplicate() {
        // Some systems use \r or \r\n; should handle \r as line boundary
        String result = StreamLogDeduplicator.removePrefixOverlap("data\r", "data\rmore");
        assertEquals("more", result);
    }

    @Test
    void multipleLinesWithOverlapShouldDeduplicate() {
        // Previous tail ends with "line2\n", current chunk is "line2\nline3\nline4\n"
        String result = StreamLogDeduplicator.removePrefixOverlap("line1\nline2\n", "line2\nline3\nline4\n");
        assertEquals("line3\nline4\n", result);
    }

    @Test
    void emptyCurrentChunkShouldReturnEmpty() {
        String result = StreamLogDeduplicator.removePrefixOverlap("previous", "");
        assertEquals("", result);
    }

    @Test
    void nullCurrentChunkShouldReturnEmpty() {
        String result = StreamLogDeduplicator.removePrefixOverlap("previous", null);
        assertEquals("", result);
    }

    @Test
    void nullPreviousTailShouldReturnFullChunk() {
        String result = StreamLogDeduplicator.removePrefixOverlap(null, "current");
        assertEquals("current", result);
    }

    @Test
    void emptyPreviousTailShouldReturnFullChunk() {
        String result = StreamLogDeduplicator.removePrefixOverlap("", "current");
        assertEquals("current", result);
    }

    @Test
    void bothEmptyShouldReturnEmpty() {
        String result = StreamLogDeduplicator.removePrefixOverlap("", "");
        assertEquals("", result);
    }

    @Test
    void longOverlapWithMultipleLineBoundariesShouldFindFirstMatch() {
        // Should match at the LONGEST overlap that satisfies line boundary condition
        String result = StreamLogDeduplicator.removePrefixOverlap("aaa\nbbb\nccc\n", "bbb\nccc\ndd");
        // The overlap could be "bbb\nccc\n" (7 chars), not "dd" (0 chars) or smaller
        assertEquals("dd", result);
    }

    @Test
    void singleCharacterOverlapShouldNotDeduplicate() {
        // Single char doesn't satisfy line boundary condition
        String result = StreamLogDeduplicator.removePrefixOverlap("a", "ab");
        assertEquals("ab", result);
    }

    @Test
    void singleCharacterWithNewlineShouldDeduplicate() {
        // "\n" is a line boundary
        String result = StreamLogDeduplicator.removePrefixOverlap("a\n", "a\nb");
        assertEquals("b", result);
    }

    @Test
    void windowsLineBoundaryRNShouldDeduplicate() {
        // Windows line ending: \r\n
        String result = StreamLogDeduplicator.removePrefixOverlap("line1\r\n", "line1\r\nline2");
        assertEquals("line2", result);
    }

    @Test
    void multipleOverlapCandidatesShouldUseConservativeMatch() {
        // Greedy match at line boundary is preferred
        String previous = "x\ny\nz\n";
        String current = "y\nz\nmore";
        String result = StreamLogDeduplicator.removePrefixOverlap(previous, current);
        // Should match "y\nz\n" (line boundary), not smaller substrings
        assertEquals("more", result);
    }

}
