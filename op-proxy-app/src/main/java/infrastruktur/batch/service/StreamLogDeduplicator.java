package infrastruktur.batch.service;

final class StreamLogDeduplicator {

    private StreamLogDeduplicator() {
        // Utility class
    }

    static String removePrefixOverlap(String previousTail, String currentChunk) {
        if (previousTail == null || previousTail.isEmpty() || currentChunk == null || currentChunk.isEmpty()) {
            return currentChunk == null ? "" : currentChunk;
        }

        int max = Math.min(previousTail.length(), currentChunk.length());
        for (int overlap = max; overlap > 0; overlap--) {
            String previousSuffix = previousTail.substring(previousTail.length() - overlap);
            String currentPrefix = currentChunk.substring(0, overlap);
            if (previousSuffix.equals(currentPrefix)) {
                // Deduplicera endast när överlappet är hela chunken eller slutar vid radslut.
                // Detta minskar risken att kapa giltig loggtext vid slumpmässig teckenmatchning.
                if (overlap == currentChunk.length() || currentPrefix.endsWith("\n") || currentPrefix.endsWith("\r")) {
                    return currentChunk.substring(overlap);
                }
            }
        }
        return currentChunk;
    }
}
