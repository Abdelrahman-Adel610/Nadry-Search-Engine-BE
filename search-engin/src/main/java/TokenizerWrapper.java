import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wrapper class to expose tokenization functionality to Node.js through java-bridge
 */
public class TokenizerWrapper {
    private static final Logger logger = LoggerFactory.getLogger(TokenizerWrapper.class);
    private final Tokenizer tokenizer;
    
    /**
     * Constructor that initializes the tokenizer with a new StopWordFilter
     */
    public TokenizerWrapper() {
        try {
            StopWordFilter stopWordFilter = new StopWordFilter();
            this.tokenizer = new Tokenizer(stopWordFilter);
            logger.info("TokenizerWrapper initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize TokenizerWrapper: {}", e.getMessage());
            throw new RuntimeException("Failed to initialize TokenizerWrapper", e);
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
}