package api;

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
     * Save search query to Supabase - using RestTemplate
     */
    public boolean saveSearchQuery(String query) {
        // 1. Check if query exists
        // Correct Supabase REST syntax: column=eq.value
         String checkUrl = UriComponentsBuilder.fromHttpUrl(supabaseUrl)
                .path("/rest/v1/Suggestions")
                .queryParam("select", "id")
                .queryParam("Suggestions", "eq." + query) // Use eq. filter correctly
                .queryParam("limit", "1")
                .encode(StandardCharsets.UTF_8) // Ensure query value is encoded
                .toUriString();

        logger.debug("Checking existence with URL: {}", checkUrl);

        try {
            HttpEntity<String> checkEntity = new HttpEntity<>(headers);
            ResponseEntity<String> checkResponse = restTemplate.exchange(
                checkUrl, HttpMethod.GET, checkEntity, String.class);

            if (checkResponse.getStatusCode().is2xxSuccessful() && checkResponse.getBody() != null) {
                logger.debug("Supabase check response body: {}", checkResponse.getBody());
                List<Map<String, Object>> existingItems = objectMapper.readValue(
                    checkResponse.getBody(),
                    new TypeReference<List<Map<String, Object>>>() {}
                );

                if (!existingItems.isEmpty()) {
                    logger.info("Query \"{}\" already exists in database (RestTemplate).", query);
                    return true; // Mimic JS: success if exists or saved
                }

                // 2. Query doesn't exist, insert it
                String insertUrl = UriComponentsBuilder.fromHttpUrl(supabaseUrl)
                        .path("/rest/v1/Suggestions")
                        .toUriString();

                Map<String, String> newRecord = new HashMap<>();
                newRecord.put("Suggestions", query);

                HttpHeaders insertHeaders = new HttpHeaders();
                insertHeaders.addAll(this.headers);
                insertHeaders.set("Prefer", "return=minimal"); // Keep minimal return

                HttpEntity<String> insertEntity = new HttpEntity<>(
                    objectMapper.writeValueAsString(newRecord),
                    insertHeaders);

                logger.debug("Inserting query with URL: {} and Body: {}", insertUrl, objectMapper.writeValueAsString(newRecord));

                ResponseEntity<String> insertResponse = restTemplate.exchange(
                    insertUrl, HttpMethod.POST, insertEntity, String.class);

                // Check specifically for 201 Created
                if (insertResponse.getStatusCode() == HttpStatus.CREATED) {
                    logger.info("Saved new search query: \"{}\" (RestTemplate)", query);
                    return true;
                } else {
                    // Log detailed error for non-201 responses during insert
                    logger.error("Error inserting query (RestTemplate). Status: {}, Body: {}", insertResponse.getStatusCode(), insertResponse.getBody());
                    return false;
                }
            } else {
                // Log detailed error for non-2xx responses during check
                logger.error("Error checking query existence (RestTemplate). Status: {}, Body: {}", checkResponse.getStatusCode(), checkResponse.getBody());
                return false;
            }

        } catch (Exception e) {
             // Log detailed error for exceptions during the whole process
            logger.error("Error saving search query '{}' (RestTemplate): {}", query, e.getMessage(), e);
            return false;
        }
    }
}
