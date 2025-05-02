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
            this.ranker = new nadry.ranker.Ranker(mongoConnectionString); // Initialize Ranker
        } catch (Exception e) {
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
                
                // Rank results based on weighted term frequency and field weights
                List<Map<String, Object>> rankedResults = rankResults(termPostings, queryTokens);

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
            }
        }

        if (pagesBag.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. Call the Ranker
        ArrayList<QueryDocument> sortedDocs = ranker.Rank(queryBag, pagesBag);

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