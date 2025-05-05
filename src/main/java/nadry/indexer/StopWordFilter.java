package nadry.indexer;


import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class StopWordFilter {
    private final Set<String> stopWords;

    public StopWordFilter() {
        this.stopWords = new HashSet<>(Arrays.asList(
            "a", "an", "and", "are", "as", "at", "be", "by", "for",
            "from", "has", "he", "in", "is", "it", "its", "of", "on",
            "that", "the", "to", "was", "were", "will", "with", "this"
        ));
    }

    public boolean isNotStopWord(String word) {
        return !stopWords.contains(word.toLowerCase());
    }
}