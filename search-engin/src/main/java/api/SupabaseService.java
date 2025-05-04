package api;

// Remove Supabase client imports
// import io.github.jan.supabase.SupabaseClient;
// import io.github.jan.supabase.SupabaseClientBuilder;
// import io.github.jan.supabase.gotrue.GoTrue;
// import io.github.jan.supabase.postgrest.Postgrest;
// import io.github.jan.supabase.postgrest.query.PostgrestResult;
// import org.json.JSONArray;

// Add back necessary imports for RestTemplate approach
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus; // <-- Add this import
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.annotation.PostConstruct; // Keep this
import java.net.URLEncoder; // Add back URL encoder
import java.nio.charset.StandardCharsets; // Add back charset
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SupabaseService {

    // Hard-coding the credentials to match the JS implementation
    private final String supabaseUrl = "https://jfznfxwatpxwqoasszgh.supabase.co";
    private final String supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Impmem5meHdhdHB4d3FvYXNzemdoIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDU4Mzg1NzIsImV4cCI6MjA2MTQxNDU3Mn0._8mpyWZ43SQcgFV6LLtxQpdVLfbqEQ2sf0XcYpeU6sw";

    // Use RestTemplate and ObjectMapper again
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private HttpHeaders headers;

    // Remove SupabaseClient field
    // private SupabaseClient client;

    public SupabaseService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }


    @PostConstruct
    public void init() {
        // Initialize headers for RestTemplate
        this.headers = new HttpHeaders();
        this.headers.set("apikey", supabaseKey);
        this.headers.set("Authorization", "Bearer " + supabaseKey);
        this.headers.setContentType(MediaType.APPLICATION_JSON);
        // Add Prefer header if needed for inserts/updates
        // this.headers.set("Prefer", "return=minimal"); // Example

        System.out.println("Supabase service initialized (using RestTemplate) with URL: " + supabaseUrl);
    }

    /**
     * Get suggestions from Supabase based on query prefix - using RestTemplate
     */
    public List<String> getSuggestions(String query, int limit) {
        try {
            // Build the URL exactly like JS does with ilike filter
            String url = supabaseUrl + "/rest/v1/Suggestions?select=Suggestions&Suggestions=ilike." +
                         encodeParameter("%" + query + "%") + "&limit=" + limit;

            // Make the request
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            // Process response
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, String>> items = objectMapper.readValue(
                    response.getBody(),
                    new TypeReference<List<Map<String, String>>>() {}
                );

                List<String> suggestions = new ArrayList<>();
                for (Map<String, String> item : items) {
                    suggestions.add(item.get("Suggestions"));
                }

                System.out.println("Successfully fetched " + suggestions.size() + " suggestions (RestTemplate)");
                return suggestions;
            } else {
                System.err.println("Error response from Supabase (RestTemplate): " + response.getStatusCode());
                return Collections.emptyList();
            }
        } catch (Exception e) {
            System.err.println("Error fetching suggestions (RestTemplate): " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    /**
     * Save search query to Supabase - using RestTemplate
     */
    public boolean saveSearchQuery(String query) {
        try {
            // Check if query exists - exactly like JS implementation with eq filter
            String checkUrl = supabaseUrl + "/rest/v1/Suggestions?select=id&Suggestions=eq." +
                             encodeParameter(query) + "&limit=1";

            HttpEntity<String> checkEntity = new HttpEntity<>(headers);
            ResponseEntity<String> checkResponse = restTemplate.exchange(
                checkUrl, HttpMethod.GET, checkEntity, String.class);

            if (checkResponse.getStatusCode().is2xxSuccessful() && checkResponse.getBody() != null) {
                // Parse the response to check if the query exists
                List<Map<String, Object>> existingItems = objectMapper.readValue(
                    checkResponse.getBody(),
                    new TypeReference<List<Map<String, Object>>>() {}
                );

                if (!existingItems.isEmpty()) {
                    // Query already exists
                    System.out.println("Query \"" + query + "\" already exists in database (RestTemplate).");
                    return true;
                }

                // Query doesn't exist, insert it
                String insertUrl = supabaseUrl + "/rest/v1/Suggestions";

                Map<String, String> newRecord = new HashMap<>();
                newRecord.put("Suggestions", query);

                // Ensure Prefer header is set for minimal return on POST
                HttpHeaders insertHeaders = new HttpHeaders();
                insertHeaders.addAll(this.headers);
                insertHeaders.set("Prefer", "return=minimal");


                HttpEntity<String> insertEntity = new HttpEntity<>(
                    objectMapper.writeValueAsString(newRecord),
                    insertHeaders); // Use headers with Prefer

                ResponseEntity<String> insertResponse = restTemplate.exchange(
                    insertUrl, HttpMethod.POST, insertEntity, String.class);

                // Check for 201 Created status specifically for successful POST
                if (insertResponse.getStatusCode() == HttpStatus.CREATED) { // Now HttpStatus can be resolved
                    System.out.println("Saved new search query: \"" + query + "\" (RestTemplate)");
                    return true;
                } else {
                    System.err.println("Error inserting query (RestTemplate). Status: " + insertResponse.getStatusCode());
                    System.err.println("Response body: " + insertResponse.getBody());
                    return false;
                }
            } else {
                System.err.println("Error checking query existence (RestTemplate). Status: " + checkResponse.getStatusCode());
                System.err.println("Response body: " + checkResponse.getBody());
                return false;
            }

        } catch (Exception e) {
            System.err.println("Error saving search query (RestTemplate): " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * URL encode a parameter safely
     */
    private String encodeParameter(String param) {
        try {
            return URLEncoder.encode(param, StandardCharsets.UTF_8.toString());
        } catch (Exception e) {
            System.err.println("Error encoding parameter: " + e.getMessage());
            return param; // Return unencoded as fallback
        }
    }
}
