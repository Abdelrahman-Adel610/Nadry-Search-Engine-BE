package nadry.api;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap; // Import for cache
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Added imports for Supabase exceptions if you want specific catches
// import io.supabase.exceptions.ApiException;
// import io.supabase.exceptions.HttpRequestException;

@RestController
@RequestMapping("/api")
public class SearchController {
    
    // Assuming SearchWrapper handles the main search logic
    private final SearchWrapper searchWrapper = new SearchWrapper(); 

    @Autowired // Inject the SupabaseService
    private SupabaseService supabaseService;

    // Simple in-memory cache for search results
    private final Map<String, CachedSearchResult> searchCache = new ConcurrentHashMap<>();

    // Inner class to hold cached data
    private static class CachedSearchResult {
        final Map<String, Object> results;
        final double searchTimeSec;

        CachedSearchResult(Map<String, Object> results, double searchTimeSec) {
            this.results = results;
            this.searchTimeSec = searchTimeSec;
        }
    }

    @GetMapping("/test")
    public String test() {
        return "Test endpoint working!";
    }

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam String query, 
            @RequestParam(defaultValue = "1") int page, 
            @RequestParam(defaultValue = "20") int limit) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Check for quoted phrases
            String searchQuery = query; // The key for caching and searching
            boolean isPhraseSearch = false;
            List<String> phrases = extractQuotedPhrases(query);
            
            if (!phrases.isEmpty()) {
                searchQuery = phrases.get(0); // Use first phrase as search query
                isPhraseSearch = true;
            }
            
            Map<String, Object> searchResults;
            double searchTimeSec;

            // Check cache
            CachedSearchResult cachedData = searchCache.get(searchQuery);

            if (cachedData != null) {
                // Cache HIT
                searchResults = cachedData.results;
                searchTimeSec = cachedData.searchTimeSec;
                System.out.println("Cache HIT for query: \"" + searchQuery + "\". Using stored time: " + searchTimeSec + "s");
            } else {
                // Cache MISS
                System.out.println("Cache MISS for query: \"" + searchQuery + "\"");
                long startTime = System.currentTimeMillis();

                // Perform search based on type
                if (isPhraseSearch) {
                    searchResults = searchWrapper.phraseSearch(searchQuery, page - 1, limit); // Convert 1-based to 0-based page
                } else {
                    searchResults = searchWrapper.search(searchQuery, page - 1, limit); // Convert 1-based to 0-based page
                }

                searchTimeSec = (System.currentTimeMillis() - startTime) / 1000.0;

                // Store in cache
                searchCache.put(searchQuery, new CachedSearchResult(searchResults, searchTimeSec));
                System.out.println("Stored results in cache for query: \"" + searchQuery + "\" with time: " + searchTimeSec + "s");
            }

            // Get the results list from the response
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> resultsList = (List<Map<String, Object>>) searchResults.get("results");
            
            // Get metadata from the search results
            int totalResults = ((Number) searchResults.getOrDefault("totalResults", 0)).intValue();
            int totalPages = ((Number) searchResults.getOrDefault("totalPages", 0)).intValue();
            int currentPage = ((Number) searchResults.getOrDefault("currentPage", 0)).intValue() + 1; // Convert 0-based to 1-based page
            
            // Tokenize the original full query for metadata
            String[] tokens = searchWrapper.tokenize(query);
            
            // Build response
            response.put("success", true);
            response.put("data", resultsList);
            response.put("totalPages", totalPages);
            response.put("currentPage", currentPage);
            response.put("totalResults", totalResults);
            response.put("tokens", tokens);
            response.put("searchTimeSec", searchTimeSec); // Use cached or calculated time
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            System.err.println("Error during search: " + e.getMessage());
            e.printStackTrace();
            
            response.put("success", false);
            response.put("message", "An error occurred during search");
            response.put("error", e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Extract phrases enclosed in double quotes from a query string
     * @param query The input query 
     * @return A list of extracted phrases
     */
    private List<String> extractQuotedPhrases(String query) {
        List<String> phrases = new ArrayList<>();
        Pattern pattern = Pattern.compile("\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(query);
        
        while (matcher.find()) {
            String phrase = matcher.group(1).trim();
            if (!phrase.isEmpty()) {
                phrases.add(phrase);
            }
        }
        
        return phrases;
    }
    
    @GetMapping("/suggestions")
    public ResponseEntity<Map<String, Object>> getSuggestions(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "5") int limit) { // Added limit parameter like JS
        Map<String, Object> response = new HashMap<>();
        try {
            List<String> suggestions;
            if (query != null && !query.isEmpty()) {
                 // Call Supabase service
                suggestions = supabaseService.getSuggestions(query, limit);
            } else {
                // Handle case with no query - maybe fetch popular suggestions?
                // For now, returning empty list as per JS logic when query is absent/empty
                 suggestions = new ArrayList<>(); 
                 // Or call supabaseService.getSuggestions("", limit) if you want default popular ones stored with empty prefix logic
            }
            
            response.put("success", true);
            response.put("data", suggestions);
            response.put("source", "database"); // Mimic JS response structure
            
            return ResponseEntity.ok(response);
        } catch (Exception e) { // Catch Supabase or other exceptions
            System.err.println("Error fetching suggestions: " + e.getMessage());
            response.put("success", false);
            response.put("message", "Error accessing suggestion database");
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    @PostMapping("/save-search")
    public ResponseEntity<Map<String, Object>> saveSearch(@RequestBody Map<String, Object> searchData) {
        Map<String, Object> response = new HashMap<>();
        String query = (String) searchData.get("query"); // Extract query from body

        if (query == null || query.trim().isEmpty()) {
             response.put("success", false);
             response.put("message", "Search query is required");
             return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        try {
            boolean processed = supabaseService.saveSearchQuery(query.trim());
            
            // The JS logic returns success even if it already existed. Mimicking that.
            if (processed) { // `processed` is true if saved OR already existed
                response.put("success", true);
                response.put("message", "Search query processed successfully"); // Matches JS message
                return ResponseEntity.ok(response);
            } else {
                 // This case means there was an error during check or insert
                response.put("success", false);
                response.put("message", "Failed to save search query due to a database error.");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response); 
            }
        } catch (Exception e) { // Catch Supabase or other exceptions
            System.err.println("Error saving search: " + e.getMessage());
            response.put("success", false);
            response.put("message", "An error occurred while saving the search query");
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}