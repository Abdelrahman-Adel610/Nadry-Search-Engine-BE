package  nadry.api;

// Import classes from the indexer package
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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

import nadry.indexer.InvertedIndex;
import nadry.indexer.InvertedIndex.FieldType;
import nadry.indexer.InvertedIndex.Posting;
import nadry.indexer.MongoDBIndexStore;
import nadry.indexer.StopWordFilter;
import nadry.indexer.Tokenizer;
import nadry.ranker.QueryDocument;
// Add imports for sentence boundary detection
import java.text.BreakIterator;

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
            this.mongoConnectionString = nadry.Config.DATABASE_URL;
            this.databaseName = nadry.Config.DATABASE_NAME;
            this.collectionName =  nadry.Config.INVERTED_INDEX_COLLECTION_NAME;
            
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
     * Class to hold search results and metadata
     */
    public static class SearchResult {
        private final List<Map<String, Object>> results;
        private final int totalResults;
        private final int totalPages;
        private final int currentPage;
        
        public SearchResult(List<Map<String, Object>> results, int totalResults, int totalPages, int currentPage) {
            this.results = results;
            this.totalResults = totalResults;
            this.totalPages = totalPages;
            this.currentPage = currentPage;
        }
        
        public List<Map<String, Object>> getResults() {
            return results;
        }
        
        public int getTotalResults() {
            return totalResults;
        }
        
        public int getTotalPages() {
            return totalPages;
        }
        
        public int getCurrentPage() {
            return currentPage;
        }
    }
    
    /**
     * Search the index using the tokenized query terms with multithreading and pagination
     * 
     * @param query The search query to tokenize and search
     * @param page The page number (0-based) to return
     * @param pageSize The number of results per page
     * @return SearchResult containing results and metadata
     */
    public SearchResult searchWithMetadata(String query, int page, int pageSize) {
        try {
            // Tokenize the query
            String[] queryTokens = tokenize(query);
            
            if (queryTokens.length == 0) {
                return new SearchResult(Collections.emptyList(), 0, 0, page);
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
                
                // Store total count before pagination
                int totalResults = rankedResults.size();
                
                // Calculate total pages
                int totalPages = (int) Math.ceil((double) totalResults / pageSize);
                
                // Apply pagination to the results
                List<Map<String, Object>> paginatedResults = paginateResults(rankedResults, page, pageSize);
                
                // Only enrich the paginated results to reduce database queries
                enrichResultsWithDocumentDetails(paginatedResults, queryTokens);
                
                return new SearchResult(paginatedResults, totalResults, totalPages, page);
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
            return new SearchResult(Collections.emptyList(), 0, 0, page);
        }
    }
    
    /**
     * Search the index using the tokenized query terms with multithreading and pagination
     * 
     * @param query The search query to tokenize and search
     * @param page The page number (0-based) to return
     * @param pageSize The number of results per page
     * @return SearchResult containing results and metadata
     */
    public SearchResult search(String query, int page, int pageSize) {
        return searchWithMetadata(query, page, pageSize);
    }
    
    /**
     * Original search method - for backward compatibility
     * Uses default pagination (first page with 10 results)
     */
    public SearchResult search(String query) {
        return search(query, 0, 10); // Default: first page with 10 results
    }
    
    /**
     * Compatibility method to support old code that expects List<Map<String, Object>> return type
     */
    public List<Map<String, Object>> searchAsList(String query, int page, int pageSize) {
        SearchResult response = search(query, page, pageSize);
        List<Map<String, Object>> results = response.getResults();
        return results != null ? results : Collections.emptyList();
    }
    
    /**
     * Original search method returning List - for backward compatibility
     */
    public List<Map<String, Object>> searchAsList(String query) {
        return searchAsList(query, 0, 10);
    }
    
    /**
     * Search the index for an exact phrase match with pagination support.
     * 
     * @param phrase The exact phrase to search for
     * @param page The page number (0-based) to return
     * @param pageSize The number of results per page
     * @return SearchResult containing results and metadata
     */
    public SearchResult phraseSearch(String phrase, int page, int pageSize) {
     
        // Validate pagination parameters
        if (page < 0) page = 0;
        if (pageSize <= 0) pageSize = 10;
        
        // Phrase is Tokenized
        String[] phraseTokens = tokenize(phrase); 
        
        // All the phrase parts are stopping words
        if (phraseTokens.length == 0) {
            return new SearchResult(Collections.emptyList(), 0, 0, page);
        }

        // If only one valid token remains, delegate to regular search
        if (phraseTokens.length == 1) {
            return searchWithMetadata(phraseTokens[0], page, pageSize);
        }

        // Get postings for the *first processed* term
        List<Posting> firstTermPostings = index.getPostings(phraseTokens[0]);

        // if first term doesn't exist anyway return EMPTY LIST
        if (firstTermPostings.isEmpty()) {
            return new SearchResult(Collections.emptyList(), 0, 0, page);
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
        
        // Check subsequent terms - ensuring EXACT phrase match by requiring consecutive positions
        for (int i = 1; i < phraseTokens.length; i++) {
            String currentTerm = phraseTokens[i];
            List<Posting> currentTermPostings = index.getPostings(currentTerm);
            Map<String, List<PotentialMatch>> nextPotentialMatches = new HashMap<>();

            // If the current term has no postings, the phrase cannot be matched
            if (currentTermPostings == null || currentTermPostings.isEmpty()) {
                potentialMatches.clear(); // Clear matches as the phrase is broken
                break;
            }

            for (Posting p : currentTermPostings) {
                String docId = p.getDocId();
                // Only consider documents that matched the previous part of the phrase
                if (potentialMatches.containsKey(docId)) {
                    List<PotentialMatch> existingMatches = potentialMatches.get(docId);
                    for (PotentialMatch match : existingMatches) {
                        // EXACT MATCH REQUIREMENT:
                        // 1. The current term must be in the same field as the previous term match.
                        if (p.getFieldTypes().contains(match.field)) {
                            // 2. The current term's position must be exactly one greater than the previous term's position.
                            for (int currentPos : p.getPositions(match.field)) {
                                if (currentPos == match.position + 1) {
                                    nextPotentialMatches.computeIfAbsent(docId, k -> new ArrayList<>())
                                                        .add(new PotentialMatch(docId, match.url, match.field, currentPos));
                                    break; 
                                }
                            }
                        }
                    }
                }
            }
            potentialMatches = nextPotentialMatches; // Update potential matches for the next iteration
            
            // If no documents contain the sequence up to this term, stop searching.
            if (potentialMatches.isEmpty()) {
                break; 
            }
        }

        // Collect final results from remaining potential matches - these contain the full exact phrase
        Set<String> matchedDocIds = potentialMatches.keySet();

        // If we have matches, create mappings for ranking
        if (!matchedDocIds.isEmpty()) {
            try {
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
                List<Map<String, Object>> results = rankAndFormatResults(phraseTokens, docTermFrequencies, docUrls);
                
                // Ensure results is not null
                if (results == null) {
                    results = Collections.emptyList();
                }
                
                // Enrich phrase search results with document details (pass phraseTokens for snippet)
                // Note: This call was previously enriching 'results', now enriching 'paginatedResults'
                // enrichResultsWithDocumentDetails(results); // Old call
                
                // Calculate pagination metadata
                int totalResults = results.size();
                int totalPages = (int) Math.ceil((double) totalResults / pageSize);
                
                // Apply pagination to get just the current page of results
                List<Map<String, Object>> paginatedResults = paginateResults(results, page, pageSize);

                // Enrich the *paginated* results with query context
                enrichResultsWithDocumentDetails(paginatedResults, phraseTokens);
                
                return new SearchResult(paginatedResults, totalResults, totalPages, page);
            } catch (Exception e) {
                return new SearchResult(Collections.emptyList(), 0, 0, page);
            }
        }

        return new SearchResult(Collections.emptyList(), 0, 0, page);
    }
    
    /**
     * Original phraseSearch method - for backward compatibility
     * Uses default pagination (first page with 10 results)
     */
    public SearchResult phraseSearch(String phrase) {
        return phraseSearch(phrase, 0, 10); // Default: first page with 10 results
    }
    
    /**
     * Compatibility method to support old code that expects List<Map<String, Object>> return type
     */
    public List<Map<String, Object>> phraseSearchAsList(String phrase, int page, int pageSize) {
        SearchResult response = phraseSearch(phrase, page, pageSize);
        List<Map<String, Object>> results = response.getResults();
        return results != null ? results : Collections.emptyList();
    }
    
    /**
     * Original phraseSearch method returning List - for backward compatibility
     */
    public List<Map<String, Object>> phraseSearchAsList(String phrase) {
        return phraseSearchAsList(phrase, 0, 10);
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
     * Enriches search results with document details from MongoDB.
     * Instead of description, it returns the first sentence containing a query term.
     * 
     * @param results List of search results to be enriched
     * @param queryTokens The tokens from the search query
     */
    private void enrichResultsWithDocumentDetails(List<Map<String, Object>> results, String[] queryTokens) {
        if (results == null || results.isEmpty()) {
            return;
        }
        
        // Extract document IDs from the results
        List<String> docIds = results.stream()
            .map(result -> (String) result.get("id"))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        if (docIds.isEmpty()) {
            return;
        }
        
        try {
            // Fetch details including content for finding matches
            Map<String, Map<String, Object>> docDetails = mongoStore.getDocumentsByIds(docIds);
            
            for (Map<String, Object> result : results) {
                String docId = (String) result.get("id");
                if (docId != null && docDetails.containsKey(docId)) {
                    Map<String, Object> details = docDetails.get(docId);
                    
                    // Add title if available
                    if (details.containsKey("title")) {
                        result.put("title", details.get("title"));
                    } else {
                        result.put("title", "No Title Available");
                    }
                    
                    // Get the content and find first match
                    String content = (String) details.get("content");
                    if (content != null && queryTokens != null && queryTokens.length > 0) {
                        // Find first match context and use it instead of description
                        String matchContext = findFirstContextMatch(content, queryTokens);
                        result.put("description", matchContext);
                    } else if (details.containsKey("description")) {
                        // Fall back to description if no match found or no content/tokens
                        result.put("description", details.get("description"));
                    } else {
                        result.put("description", "No description available.");
                    }
                } else {
                    // Handle cases where doc details might be missing
                    result.putIfAbsent("title", "No Title Available");
                    result.put("description", "Details not available.");
                }
            }
            
        } catch (Exception e) {
            // Add default values in case of error during enrichment
            for (Map<String, Object> result : results) {
                result.putIfAbsent("title", "Error fetching title");
                result.put("description", "Error fetching context.");
            }
        }
    }
    
    /**
     * Finds the first sentence in the content that contains any of the query tokens.
     * 
     * @param content The document content to search within
     * @param queryTokens The search query tokens to look for
     * @return The first sentence containing any query token, or a fallback if none found
     */
    private String findFirstContextMatch(String content, String[] queryTokens) {
        if (content == null || content.isEmpty() || queryTokens == null || queryTokens.length == 0) {
            return "No content available or no valid search terms.";
        }
        
        String lowerCaseContent = content.toLowerCase();
        
        for (String token : queryTokens) {
            if (token == null || token.isEmpty()) continue;
            
            String lowerCaseToken = token.toLowerCase();
            int matchIndex = lowerCaseContent.indexOf(lowerCaseToken);
            
            if (matchIndex != -1) {
                // Found a match, extract the sentence containing it
                BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.US);
                iterator.setText(content);
                
                // Find sentence boundaries
                int start = iterator.preceding(matchIndex + 1);
                if (start == BreakIterator.DONE) {
                    start = 0; // Start of text if no sentence break found before match
                }
                
                int end = iterator.following(matchIndex);
                if (end == BreakIterator.DONE) {
                    end = content.length(); // End of text if no sentence break found after match
                }
                
                // Extract the sentence
                String sentence = content.substring(start, end).trim();
                
                // Truncate very long sentences for better display
                final int MAX_LENGTH = 240;
                if (sentence.length() > MAX_LENGTH) {
                    // Try to center the matched token in the snippet
                    int tokenPosition = sentence.toLowerCase().indexOf(lowerCaseToken);
                    int snippetStart = Math.max(0, tokenPosition - (MAX_LENGTH/3));
                    int snippetEnd = Math.min(sentence.length(), snippetStart + MAX_LENGTH);
                    
                    // Add ellipsis if truncated
                    String prefix = snippetStart > 0 ? "..." : "";
                    String suffix = snippetEnd < sentence.length() ? "..." : "";
                    
                    sentence = prefix + sentence.substring(snippetStart, snippetEnd) + suffix;
                }
                
                return sentence;
            }
        }
        
        // If no sentences contain any query tokens, return the first sentence as fallback
        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.US);
        iterator.setText(content);
        int start = iterator.first();
        int end = iterator.next();
        
        if (end != BreakIterator.DONE) {
            String firstSentence = content.substring(start, end).trim();
            // Truncate if too long
            if (firstSentence.length() > 200) {
                firstSentence = firstSentence.substring(0, 200) + "...";
            }
            return firstSentence;
        }
        
        // If no sentences found at all, return beginning of content
        if (content.length() > 200) {
            return content.substring(0, 200) + "...";
        }
        
        return content;
    }


    /**
     * Helper method to paginate search results
     * 
     * @param results The full list of results
     * @param page The page number (0-based)
     * @param pageSize The number of results per page
     * @return A sublist containing only the requested page of results
     */
    private List<Map<String, Object>> paginateResults(List<Map<String, Object>> results, int page, int pageSize) {
        // Validate inputs
        if (page < 0) page = 0;
        if (pageSize <= 0) pageSize = 10; // Default to 10 results per page (changed from 200)
        
        // Calculate start and end indices
        int startIndex = page * pageSize;
        int endIndex = Math.min(startIndex + pageSize, results.size());
        
        // Return empty list if startIndex is beyond available results
        if (startIndex >= results.size()) {
            return Collections.emptyList();
        }
        
        List<Map<String, Object>> paginatedResults = results.subList(startIndex, endIndex);
        
        return paginatedResults;
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