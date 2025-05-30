package api;
import indexer.*;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;


public class SearchEngine {
    private static final Logger logger = LoggerFactory.getLogger(SearchEngine.class);
    private final Tokenizer tokenizer;
    private final InvertedIndex index;
    private final Map<String, ProcessedDocument> documentStore;

    public SearchEngine(Tokenizer tokenizer, InvertedIndex index, Map<String, ProcessedDocument> documentStore) {
        this.tokenizer = tokenizer;
        this.index = index;
        this.documentStore = documentStore;
    }

    public List<SearchResult> search(String query) {
        logger.info("Processing query: {}", query);

        // Tokenize the query
        List<String> queryTokens = tokenizer.tokenize(query);
        if (queryTokens.isEmpty()) {
            logger.warn("No valid tokens found in query: {}", query);
            return Collections.emptyList();
        }
        logger.debug("Query tokens: {}", queryTokens);

        // Search inverted index and rank results
        Map<String, Double> docScores = new HashMap<>();
        for (String token : queryTokens) {
            List<InvertedIndex.Posting> postings = index.getPostings(token);
            for (InvertedIndex.Posting posting : postings) {
                String docId = posting.getDocId();
                double score = posting.getWeight(); // Use field-based weight
                docScores.merge(docId, score, Double::sum);
            }
        }

        // Convert to SearchResult and sort by score
        return docScores.entrySet().stream()
                .map(entry -> {
                    String docId = entry.getKey();
                    ProcessedDocument doc = documentStore.get(docId);
                    if (doc == null) {
                        logger.warn("Document not found for docId: {}", docId);
                        return null;
                    }
                    return new SearchResult(
                            docId,
                            doc.getUrl(),
                            doc.getTitle(),
                            doc.getDescription(),
                            entry.getValue()
                    );
                })
                .filter(Objects::nonNull)
                .sorted((r1, r2) -> Double.compare(r2.getScore(), r1.getScore()))
                .limit(10)
                .collect(Collectors.toList());
    }
}