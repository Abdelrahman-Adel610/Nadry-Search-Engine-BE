package api;

// Import classes from the indexer package
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger; // Import Pattern
import org.slf4j.LoggerFactory;

import indexer.InvertedIndex;
import indexer.InvertedIndex.FieldType;
import indexer.InvertedIndex.Posting;
import indexer.MongoDBIndexStore;
import indexer.StopWordFilter;
import indexer.Tokenizer;
import nadry.ranker.QueryDocument;

/**
 * Wrapper class to expose tokenization and search functionality to Node.js through java-bridge
 */
public class SearchWrapper {
    private static final Logger logger = LoggerFactory.getLogger(SearchWrapper.class);
    private final Tokenizer tokenizer;
    private final InvertedIndex index;
    private final String mongoConnectionString;
    private final String databaseName;
    private final String collectionName;
    private final nadry.ranker.Ranker ranker; // Add Ranker instance
    private final MongoDBIndexStore mongoStore; // Add MongoDBIndexStore instance

    /**
     * Constructor that initializes the tokenizer with a new StopWordFilter
     */
    public SearchWrapper() {
        try {
            // MongoDB configuration - get from environment or use default
            this.mongoConnectionString =  "mongodb+srv://admin:admin@cluster0.wtcajo8.mongodb.net/?retryWrites=true&w=majority&appName=Cluster0";
            this.databaseName = "search_engine"; // <-- Updated database name
            this.collectionName = "inverted_index";
            
            StopWordFilter stopWordFilter = new StopWordFilter();
            this.tokenizer = new Tokenizer(stopWordFilter);
            this.index = new InvertedIndex(mongoConnectionString, databaseName, collectionName);
            this.ranker = new nadry.ranker.Ranker(mongoConnectionString); // Initialize Ranker
            this.mongoStore = new MongoDBIndexStore(mongoConnectionString, databaseName, collectionName); // Initialize mongoStore
        } catch (Exception e) {
            logger.error("Failed to initialize SearchWrapper", e); // Log error
            throw new RuntimeException("Failed to initialize SearchWrapper", e);
        }
    }
    
    /**
     * Tokenizes the provided text and returns an array of tokens
     * 
     * @param text The text to tokenize
     * @return Array of tokens
     */
    public String[] tokenize(String text) {
        if (text == null || text.isEmpty()) {
            return new String[0];
        }
        
        List<String> tokens = tokenizer.tokenize(text);
        return tokens.toArray(new String[0]);
    }
    
    /**
     * Search the index using the tokenized query terms with multithreading
     * 
     * @param query The search query to tokenize and search
     * @return List of search results with document URLs and relevance scores
     */
    public List<Map<String, Object>> search(String query) {
        try {
            // Tokenize the query
            String[] queryTokens = tokenize(query);
            
            if (queryTokens.length == 0) {
                return Collections.emptyList();
            }
            
            // Create a thread pool for parallel processing
            int threadPoolSize = Math.min(queryTokens.length, Runtime.getRuntime().availableProcessors());
            ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
            
            try {
                // Get postings for each term in parallel using separate threads
                Map<String, List<Posting>> termPostings = new ConcurrentHashMap<>();
                List<Future<?>> futures = new ArrayList<>();
                
                // Submit tasks to the executor
                for (String token : queryTokens) {
                    futures.add(executor.submit(() -> {
                        try {
                            List<Posting> postings = index.getPostings(token);
                            if (postings != null && !postings.isEmpty()) {
                                termPostings.put(token, postings);
                            }
                        } catch (Exception e) {
                        }
                    }));
                }
                
                // Wait for all tasks to complete
                for (Future<?> future : futures) {
                    try {
                        future.get(10, TimeUnit.SECONDS); // Add timeout to prevent hanging
                    } catch (Exception e) {
                    }
                }
                
                // Create queryBag for ranking
                Map<String, Integer> queryBag = new HashMap<>();
                for (String token : queryTokens) {
                    queryBag.merge(token, 1, Integer::sum);
                }

                // Create pagesBag for ranking
                Map<String, Map<String, Integer>> docTermFrequencies = new HashMap<>();
                Map<String, String> docUrls = new HashMap<>(); // Store URLs by docId

                // Process each posting
                for (Map.Entry<String, List<Posting>> entry : termPostings.entrySet()) {
                    String term = entry.getKey();
                    List<Posting> postings = entry.getValue();
                    for (Posting posting : postings) {
                        String docId = posting.getDocId();
                        String url = posting.getUrl();
                        docUrls.putIfAbsent(docId, url);
                        
                        // Add term frequency to document
                        docTermFrequencies.computeIfAbsent(docId, k -> new HashMap<>())
                                          .merge(term, posting.getFrequency(), Integer::sum);
                    }
                }

             
                List<Map<String, Object>> rankedResults = rankAndFormatResults(
                    queryTokens, docTermFrequencies, docUrls);
                
               
                enrichResultsWithDocumentDetails(rankedResults);

                return rankedResults;
            } finally {
                // Properly shutdown the executor service
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    /**
     * Search the index for an exact phrase match.
     * 
     * @param phrase The exact phrase to search for.
     * @return List of search results containing the phrase.
     */
    public List<Map<String, Object>> phraseSearch(String phrase) {
        logger.info("Starting phrase search for: \"{}\"", phrase);
     
        List<Map<String, Object>> results = new ArrayList<>();
        
        //Phrase is Tokenized
        String[] phraseTokens = tokenize(phrase); 
        //All the phrase parts are stopping words
        if (phraseTokens.length == 0) {
            logger.warn("Phrase tokenization resulted in zero valid processed tokens.");
            return Collections.emptyList();
        }

        // If only one valid token remains, delegate to regular search
        if (phraseTokens.length == 1) {
            logger.info("Phrase has only one valid token after processing ('{}'), delegating to regular search.", phraseTokens[0]);
            return search(phraseTokens[0]); // Search for the single processed token
        }

        // Get postings for the *first processed* term
        List<Posting> firstTermPostings = index.getPostings(phraseTokens[0]);

        // if first term doesn't exist anyway return EMPTY LIST
        if (firstTermPostings.isEmpty()) {
            logger.info("No postings found for the first processed term: {}", phraseTokens[0]);
            return Collections.emptyList();
        }

        // Map to store potential matches <DocId, List<PotentialMatch>>
        Map<String, List<PotentialMatch>> potentialMatches = new HashMap<>();

        // Populate potential matches based on the first term
        for (Posting p : firstTermPostings) {
            for (FieldType field : p.getFieldTypes()) {
                for (int pos : p.getPositions(field)) {
                    potentialMatches.computeIfAbsent(p.getDocId(), k -> new ArrayList<>())
                                    .add(new PotentialMatch(p.getDocId(), p.getUrl(), field, pos));
                }
            }
        }
        
        // DEBUUUUUUUUUUUUUUGGGGGGGGGG LOG
{        StringBuilder sb = new StringBuilder();
        sb.append("\n==== POTENTIAL MATCHES DETAILS ====\n");
        potentialMatches.forEach((docId, matches) -> {
            sb.append("  DocID: ").append(docId).append("\n");
            sb.append("    URL: ").append(matches.isEmpty() ? "N/A" : matches.get(0).url).append("\n");
            sb.append("    Total Matches: ").append(matches.size()).append("\n");
            
            // Print each match individually
            int matchCounter = 1;
            for (PotentialMatch match : matches) {
                sb.append("    Match #").append(matchCounter++).append(": ")
                  .append("Field=").append(match.field)
                  .append(", Position=").append(match.position)
                  .append("\n");
            }
        });
        sb.append("====================================\n");
        logger.info(sb.toString()); // Changed to INFO level to ensure visibility
        
        logger.info("Initial potential matches based on first term: {}", potentialMatches.size());}

        // DEBUUUUUUUUUUUUUUGGGGGGGGGG LOG


        
        // Check subsequent terms
        for (int i = 1; i < phraseTokens.length; i++) {
            String currentTerm = phraseTokens[i];
            List<Posting> currentTermPostings = index.getPostings(currentTerm);
            Map<String, List<PotentialMatch>> nextPotentialMatches = new HashMap<>();

            for (Posting p : currentTermPostings) {
                String docId = p.getDocId();
                if (potentialMatches.containsKey(docId)) {
                    List<PotentialMatch> existingMatches = potentialMatches.get(docId);
                    for (PotentialMatch match : existingMatches) {
                        // Check if the current term appears in the *same field* at the *next position*
                        if (p.getFieldTypes().contains(match.field)) {
                            for (int currentPos : p.getPositions(match.field)) {
                                if (currentPos == match.position + 1) {
                                    // Found a consecutive term, update the potential match's position
                                    nextPotentialMatches.computeIfAbsent(docId, k -> new ArrayList<>())
                                                        .add(new PotentialMatch(docId, match.url, match.field, currentPos));
                                    break; // Move to the next potential match for this docId
                                }
                            }
                        }
                    }
                }
            }
            potentialMatches = nextPotentialMatches; // Update potential matches for the next iteration
            logger.debug("Potential matches after term '{}': {}", currentTerm, potentialMatches.size());
            if (potentialMatches.isEmpty()) {
                logger.info("No potential matches left after term: {}", currentTerm);
                break; // No need to check further terms if no matches remain
            }
        }

        // Collect final results from remaining potential matches
        Set<String> matchedDocIds = potentialMatches.keySet();
        logger.info("Found {} documents containing the exact phrase.", matchedDocIds.size());

        // If we have matches, create mappings for ranking
        if (!matchedDocIds.isEmpty()) {
            // Create mappings from matched documents
            Map<String, Map<String, Integer>> docTermFrequencies = new HashMap<>();
            Map<String, String> docUrls = new HashMap<>();
            
            // Populate term frequencies (all terms in phrase have frequency 1)
            for (String docId : matchedDocIds) {
                Map<String, Integer> docTerms = new HashMap<>();
                for (String token : phraseTokens) {
                    docTerms.put(token, 1);
                }
                String url = potentialMatches.get(docId).get(0).url;
                docTermFrequencies.put(docId, docTerms);
                docUrls.put(docId, url);
            }
            
            // Use helper method to rank and format results
            logger.info("Ranking {} documents that match the phrase", matchedDocIds.size());
            results = rankAndFormatResults(phraseTokens, docTermFrequencies, docUrls);
            
            // Enrich phrase search results with document details
            enrichResultsWithDocumentDetails(results);
        }

        logger.info("Phrase search completed. Returning {} results.", results.size());
        return results;
    }

    /**
     * Helper method to rank documents and format the results
     * 
     * @param queryTokens Array of query tokens
     * @param docTermFrequencies Map of document IDs to their term frequency maps
     * @param docUrls Map of document IDs to their URLs
     * @return List of formatted search results
     */
    private List<Map<String, Object>> rankAndFormatResults(
            String[] queryTokens, 
            Map<String, Map<String, Integer>> docTermFrequencies, 
            Map<String, String> docUrls) {
        
        // Create query bag with frequency counts
        Map<String, Integer> queryBag = new HashMap<>();
        for (String token : queryTokens) {
            queryBag.merge(token, 1, Integer::sum);
        }
        
        // Create documents for ranking
        List<QueryDocument> pagesBag = new ArrayList<>();
        for (Map.Entry<String, Map<String, Integer>> entry : docTermFrequencies.entrySet()) {
            String url = docUrls.get(entry.getKey());
            String id = entry.getKey();

            if (url != null) {
                pagesBag.add(new QueryDocument(id, url, entry.getValue()));
            }
        }
        
        if (pagesBag.isEmpty()) {
            return Collections.emptyList();
        }
        
        // Rank documents
        List<QueryDocument> rankedDocs = ranker.Rank(queryBag, pagesBag);
        
        // Convert to result format
        return rankedDocs.stream()
            .map(doc -> {
//                Map<String, Object> result = new HashMap<>();
//                result.put("url", doc.GetURL());
//                result.put("score", doc.getScore());
//                result.put("title", doc.getTitle());
//                result.put("description", doc.getDescription());
//                result.put("popularityScore", doc.GetPopularityScore());
//                result.put("relevence_score", doc.GetRelevenceScore());
//                return result;
            	return toMap(doc);
            })
            .collect(Collectors.toList());
    }
    
    public static Map<String, Object> toMap(Object obj) {
        Map<String, Object> result = new HashMap<>();
        Class<?> objClass = obj.getClass();

        for (Field field : objClass.getDeclaredFields()) {
            field.setAccessible(true); // allows access to private fields
            try {
                Object value = field.get(obj);
                result.put(field.getName(), value);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        return result;
    }

    /**
     * Enriches search results with document details from MongoDB (title, description)
     * 
     * @param results List of search results to be enriched
     */
    private void enrichResultsWithDocumentDetails(List<Map<String, Object>> results) {
        if (results == null || results.isEmpty()) {
            return;
        }
        
        // Extract document IDs from the results
        List<String> docIds = results.stream()
            .map(result -> (String) result.get("id"))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        if (docIds.isEmpty()) {
            logger.warn("No valid document IDs found in search results");
            return;
        }
        
        try {
            Map<String, Map<String, Object>> docDetails = mongoStore.getDocumentsByIds(docIds);
            
            for (Map<String, Object> result : results) {
                String docId = (String) result.get("id");
                if (docId != null && docDetails.containsKey(docId)) {
                    Map<String, Object> details = docDetails.get(docId);
                    // Add title and description if available
                    if (details.containsKey("title")) {
                        result.put("title", details.get("title"));
                    }
                    if (details.containsKey("description")) {
                        result.put("description", details.get("description"));
                    }
                }
            }
            
            logger.info("Enriched {} search results with document details", results.size());
        } catch (Exception e) {
            logger.error("Error enriching results with document details", e);
        }
    }

    // Helper class to track potential phrase matches
    private static class PotentialMatch {
    	final String id;
        final String url;
        final FieldType field;
        final int position; // Position of the *last* matched term in the sequence

        PotentialMatch(String id, String url, FieldType field, int position) {
        	this.id = id;
            this.url = url;
            this.field = field;
            this.position = position;
        }
    }
}