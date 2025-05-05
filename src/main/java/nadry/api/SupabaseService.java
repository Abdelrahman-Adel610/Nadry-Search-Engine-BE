package nadry.api;

import org.springframework.beans.factory.annotation.Value; // Import @Value
import org.slf4j.Logger; // Use SLF4J for logging
import org.slf4j.LoggerFactory; // Use SLF4J for logging
import org.springframework.web.util.UriComponentsBuilder; // For building URIs safely

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
import java.nio.charset.StandardCharsets; // Add back charset
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.client.HttpClientErrorException; // Add this import

@Service
public class SupabaseService {

    private static final Logger logger = LoggerFactory.getLogger(SupabaseService.class); // Logger instance

    // Inject values from application.yml
    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.key}")
    private String supabaseKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private HttpHeaders headers;

    public SupabaseService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    public void init() {
        this.headers = new HttpHeaders();
        this.headers.set("apikey", supabaseKey);
        this.headers.set("Authorization", "Bearer " + supabaseKey);
        this.headers.setContentType(MediaType.APPLICATION_JSON);
        logger.info("Supabase service initialized (RestTemplate) for URL: {}", supabaseUrl);
        if (supabaseKey == null || supabaseKey.isEmpty()) {
             logger.error("Supabase Key is missing!");
        }
         if (supabaseUrl == null || supabaseUrl.isEmpty()) {
             logger.error("Supabase URL is missing!");
        }
    }

    /**
     * Get suggestions from Supabase based on query prefix - using RestTemplate
     */
    public List<String> getSuggestions(String query, int limit) {
        // Use UriComponentsBuilder for safer URL construction and encoding
        // Correct Supabase REST syntax: column=ilike.*pattern*
        String url = UriComponentsBuilder.fromHttpUrl(supabaseUrl)
                .path("/rest/v1/Suggestions")
                .queryParam("select", "Suggestions")
                // Apply ilike filter correctly - only encode the query part
                .queryParam("Suggestions", "ilike.*" + query + "*") // Use * wildcard for ilike via REST
                .queryParam("limit", String.valueOf(limit))
                .encode(StandardCharsets.UTF_8) // Ensure proper encoding of the query part within the pattern
                .toUriString();

        logger.debug("Fetching suggestions from URL: {}", url); // Log the URL

        try {
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                logger.debug("Supabase suggestions response body: {}", response.getBody()); // Log success response
                List<Map<String, String>> items = objectMapper.readValue(
                    response.getBody(),
                    new TypeReference<List<Map<String, String>>>() {}
                );

                List<String> suggestions = new ArrayList<>();
                for (Map<String, String> item : items) {
                    suggestions.add(item.get("Suggestions"));
                }

                logger.info("Successfully fetched {} suggestions (RestTemplate) for query '{}'", suggestions.size(), query);
                return suggestions;
            } else {
                logger.error("Error response from Supabase suggestions (RestTemplate): Status={}, Body={}", response.getStatusCode(), response.getBody());
                return Collections.emptyList();
            }
        } catch (Exception e) {
            logger.error("Error fetching suggestions (RestTemplate) for query '{}': {}", query, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Saves a search query to the Supabase 'Suggestions' table.
     * Handles potential duplicate entries gracefully.
     *
     * @param query The search query string to save.
     */
    public void saveSearchQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            logger.warn("Attempted to save an empty or null search query.");
            return;
        }

        String url = UriComponentsBuilder.fromHttpUrl(supabaseUrl)
                .path("/rest/v1/Suggestions")
                .encode(StandardCharsets.UTF_8)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("apikey", supabaseKey); // Use the injected key
        headers.set("Authorization", "Bearer " + supabaseKey); // Use the injected key
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Prefer", "return=minimal"); // Don't need the inserted row back

        // Create the request body
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("Suggestions", query.trim()); // Ensure trimming before saving

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("Successfully saved search query: '{}'", query.trim());
            } else {
                // Log unexpected non-2xx success responses
                logger.warn("Received non-successful status code {} when saving query '{}': {}",
                        response.getStatusCode(), query.trim(), response.getBody());
            }
        } catch (HttpClientErrorException e) {
            // Specifically handle the 409 Conflict error (duplicate entry)
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                logger.info("Search query '{}' already exists in suggestions. No action needed.", query.trim());
                // Optionally parse the error message if more details are needed, but often just logging is enough.
                // Example: logger.debug("Conflict details: {}", e.getResponseBodyAsString());
            } else {
                // Log other client-side HTTP errors (4xx)
                logger.error("HTTP client error saving search query '{}': Status={}, Body={}",
                        query.trim(), e.getStatusCode(), e.getResponseBodyAsString(), e);
                // Depending on requirements, you might re-throw or handle differently
            }
        } catch (Exception e) {
            // Catch all other exceptions (network issues, server errors 5xx, etc.)
            logger.error("Error saving search query '{}' to Supabase: {}", query.trim(), e.getMessage(), e);
            // Depending on requirements, you might re-throw or handle differently
        }
    }
}
