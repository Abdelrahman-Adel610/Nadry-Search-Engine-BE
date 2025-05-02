package api;

// Import classes from the indexer package
import indexer.StopWordFilter;
import indexer.Tokenizer;
import indexer.InvertedIndex;
import indexer.MongoDBIndexStore;
import indexer.InvertedIndex.Posting;
import indexer.InvertedIndex.FieldType;
// Import Ranker classes
import nadry.ranker.Ranker;
import nadry.ranker.QueryDocument;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

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

    /**
     * Constructor that initializes the tokenizer with a new StopWordFilter
     */
    public SearchWrapper() {
        try {
            // MongoDB configuration - get from environment or use default
            this.mongoConnectionString = System.getenv("MONGO_URI") != null
                ? System.getenv("MONGO_URI")
                : "mongodb://localhost:27017/search_engine";
            this.databaseName = "search_engine";
            this.collectionName = "inverted_index";
            
            StopWordFilter stopWordFilter = new StopWordFilter();
            this.tokenizer = new Tokenizer(stopWordFilter);
            this.index = new InvertedIndex(mongoConnectionString, databaseName, collectionName);
            this.ranker = new nadry.ranker.Ranker(); // Initialize Ranker
            logger.info("SearchWrapper initialized successfully with MongoDB connection and Ranker");
        } catch (Exception e) {
            logger.error("Failed to initialize SearchWrapper: {}", e.getMessage());
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
            logger.info("SearchWrapper: Received search query: \"{}\"", query);
            System.out.println("============== SEARCH STARTED ==============");
            System.out.println("Query: \"" + query + "\"");
            
            // Tokenize the query
            String[] queryTokens = tokenize(query);
            
            if (queryTokens.length == 0) {
                logger.warn("Empty query after tokenization");
                System.out.println("Query tokenized to zero tokens. No results.");
                System.out.println("============== SEARCH ENDED ==============");
                return Collections.emptyList();
            }
            
            logger.info("SearchWrapper: Tokenized query into {} tokens: {}", queryTokens.length, Arrays.toString(queryTokens));
            
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
                            logger.debug("Fetching postings for token: {}", token);
                            List<Posting> postings = index.getPostings(token);
                            if (postings != null && !postings.isEmpty()) {
                                termPostings.put(token, postings);
                                logger.debug("Found {} postings for token: {}", postings.size(), token);
                            }
                        } catch (Exception e) {
                            logger.error("Error retrieving postings for token {}: {}", token, e.getMessage());
                        }
                    }));
                }
                
                // Wait for all tasks to complete
                for (Future<?> future : futures) {
                    try {
                        future.get(10, TimeUnit.SECONDS); // Add timeout to prevent hanging
                    } catch (Exception e) {
                        logger.error("Error in search task execution: {}", e.getMessage());
                    }
                }
                
                // Log details about the collected postings
                System.out.println("\n=== INDEXER SEARCH RESULTS (Before Ranking) ===");
                System.out.println("Total tokens processed: " + queryTokens.length);
                System.out.println("Tokens with postings found: " + termPostings.size());
                
                // Print the entire termPostings map as one entity
                System.out.println("Complete Term Postings Map: " + termPostings); 

                // Log the fetched postings map content (optional detailed view)
                /*
                if (termPostings.isEmpty()) {
                    System.out.println("No postings found for any query tokens.");
                } else {
                    System.out.println("Fetched Postings Map Content (Detailed):");
                    termPostings.forEach((term, postings) -> {
                        System.out.println("  Term: '" + term + "', Postings Count: " + postings.size());
                    });
                }
                */

                // Log each token's postings (existing detailed log)
                for (Map.Entry<String, List<Posting>> entry : termPostings.entrySet()) {
                    String token = entry.getKey();
                    List<Posting> postings = entry.getValue();
                    System.out.println("\nToken \"" + token + "\" found in " + postings.size() + " documents:");
                    
                    // Show the first 5 postings details for each token
                    int count = 0;
                    for (Posting posting : postings) {
                        if (count++ < 5) {
                            System.out.println("  - DocID: " + posting.getDocId() + 
                                             ", URL: " + posting.getUrl() + 
                                             ", Weight: " + posting.getWeight());
                        } else {
                            System.out.println("  - ... and " + (postings.size() - 5) + " more");
                            break;
                        }
                    }
                }
                
                logger.info("Found postings for {} out of {} query tokens", termPostings.size(), queryTokens.length);
                
                // Rank results based on weighted term frequency and field weights
                List<Map<String, Object>> rankedResults = rankResults(termPostings, queryTokens);

                // Log the direct output of the rankResults method
                System.out.println("\n=== Direct Output from rankResults ===");
                System.out.println(rankedResults);
                
                // Log the final ranked results (top 10 with all fields)
                System.out.println("\n=== FINAL RANKED RESULTS (Top 10) ===");
                System.out.println("Total results: " + rankedResults.size());
                
                int resultCount = 0;
                for (Map<String, Object> result : rankedResults) {
                    if (resultCount++ < 10) {
                        // Print all relevant fields from the result map
                        System.out.println("Result #" + resultCount + ": " +
                                         "URL=" + result.get("url") + 
                                         ", Title=" + result.get("title") + // Added Title
                                         ", Description=" + result.get("description") + // Added Description
                                         ", Score=" + result.get("score"));
                    } else {
                        System.out.println("... and " + (rankedResults.size() - 10) + " more results");
                        break;
                    }
                }
                System.out.println("============== SEARCH ENDED ==============");
                
                logger.info("Found {} results for query: {}", rankedResults.size(), query);
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
            logger.error("Error during search: {}", e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    /**
     * Rank search results using nadry.ranker.Ranker
     * 
     * @param termPostings Map of terms to their posting lists
     * @param queryTokens Array of query tokens
     * @return List of ranked results as maps
     */
    private List<Map<String, Object>> rankResults(Map<String, List<Posting>> termPostings, String[] queryTokens) {
        
        // 1. Create queryBag (term frequencies for the query)
        Map<String, Integer> queryBag = new HashMap<>();
        for (String token : queryTokens) {
            queryBag.merge(token, 1, Integer::sum);
        }

        // 2. Create pagesBag (List of QueryDocument)
        Map<String, Map<String, Integer>> docTermFrequencies = new HashMap<>();
        Map<String, String> docUrls = new HashMap<>(); // To store URL per docId

        for (Map.Entry<String, List<Posting>> entry : termPostings.entrySet()) {
            String term = entry.getKey();
            List<Posting> postings = entry.getValue();
            for (Posting posting : postings) {
                String docId = posting.getDocId();
                String url = posting.getUrl();
                docUrls.putIfAbsent(docId, url); // Store URL

                // Get frequency of the current term in this document
                int frequency = posting.getFrequency(); // Total frequency across all fields for this term in this doc

                // Add/Update term frequency for the document
                docTermFrequencies.computeIfAbsent(docId, k -> new HashMap<>())
                                  .merge(term, frequency, Integer::sum);
            }
        }

        List<QueryDocument> pagesBag = new ArrayList<>();
        for (Map.Entry<String, Map<String, Integer>> entry : docTermFrequencies.entrySet()) {
            String docId = entry.getKey();
            String url = docUrls.get(docId); // Retrieve URL
            Map<String, Integer> termFreqMap = entry.getValue();
            // Note: QueryDocument constructor doesn't take docId, only URL.
            // We assume the URL is the primary identifier needed by the Ranker's DB interaction.
            if (url != null) {
                 pagesBag.add(new QueryDocument(url, termFreqMap));
            } else {
                logger.warn("No URL found for docId: {}", docId);
            }
        }

        if (pagesBag.isEmpty()) {
            logger.info("No documents found containing query terms. Returning empty results.");
            return Collections.emptyList();
        }

        // 3. Call the Ranker
        logger.info("Calling nadry.ranker.Ranker with {} query terms and {} documents.", queryBag.size(), pagesBag.size());
        ArrayList<QueryDocument> sortedDocs = ranker.Rank(queryBag, pagesBag);
        logger.info("Ranker returned {} sorted documents.", sortedDocs.size());

        // Log the direct output from the Ranker
        System.out.println("\n=== Direct Output from nadry.ranker.Ranker.Rank ===");
        System.out.println(sortedDocs);


        // 4. Transform sortedDocs back to List<Map<String, Object>> including title and description
        return sortedDocs.stream()
            .map(doc -> {
                Map<String, Object> result = new HashMap<>();
                result.put("url", doc.GetURL()); 
                result.put("score", doc.getScore()); 
                result.put("title", doc.getTitle()); // Add title
                result.put("description", doc.getDescription()); // Add description
                return result;
            })
            .collect(Collectors.toList());
    }
}