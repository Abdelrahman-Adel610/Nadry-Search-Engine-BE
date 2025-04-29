
/**
 * A minimal tokenizer class for testing Java-Node.js bridge
 */
public class MinimalTokenizer {
    
    /**
     * Simple tokenize method that splits on spaces
     */
    public String[] tokenize(String text) {
        if (text == null || text.isEmpty()) {
            return new String[0];
        }
        
        // Simple implementation that just splits on spaces
        return text.toLowerCase().split("\\s+");
    }
}
