package indexer;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tartarus.snowball.ext.englishStemmer;

public class Tokenizer {
    private static final Logger logger = LoggerFactory.getLogger(Tokenizer.class);
    private final StopWordFilter stopWordFilter;
    private final englishStemmer stemmer;
    
    private static final Pattern EMAIL_PATTERN = 
        Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}");
    private static final Pattern URL_PATTERN = 
        Pattern.compile("(https?://|www\\.)[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}[^\\s]*");
    private static final Pattern NUMBER_PATTERN = 
        Pattern.compile("\\b\\d+(\\.\\d+)?\\b");
    
    public Tokenizer(StopWordFilter stopWordFilter) {
        this.stopWordFilter = stopWordFilter;
        this.stemmer = new englishStemmer();
    }
    
    public List<String> tokenize(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        
        text = Normalizer.normalize(text, Normalizer.Form.NFC);
        List<String> specialTokens = extractSpecialTokens(text);
        String processableText = replaceSpecialTokens(text);
        
        String[] tokens = processableText.toLowerCase()
                .replaceAll("[^a-z0-9\\s_]", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .split("\\s+");
        
        List<String> processedTokens = Arrays.stream(tokens)
                .filter(token -> token.length() >= 2 && token.length() <= 50)
                .filter(token -> token.equals("_email_") || token.equals("_num_") || stopWordFilter.isNotStopWord(token))
                .map(this::applyStemming)
                .collect(Collectors.toList());
        
        processedTokens.addAll(specialTokens);
        return processedTokens;
    }
    
    private String applyStemming(String token) {
        if (token.length() <= 3 || token.equals("_email_") || token.equals("_num_")) {
            logger.debug("Skipping stemming for token: {}", token);
            return token;
        }
        
        // Synchronize access to stemmer for thread safety
        synchronized (stemmer) {
            stemmer.setCurrent(token);
            stemmer.stem();
            String stemmed = stemmer.getCurrent();
            logger.debug("Stemmed {} to {}", token, stemmed);
            return stemmed;
        }
    }
    
    private List<String> extractSpecialTokens(String text) {
        List<String> specialTokens = new ArrayList<>();
        
        Matcher emailMatcher = EMAIL_PATTERN.matcher(text);
        while (emailMatcher.find()) {
            specialTokens.add("email:" + emailMatcher.group().toLowerCase());
        }
        
        Matcher urlMatcher = URL_PATTERN.matcher(text);
        while (urlMatcher.find()) {
            specialTokens.add("url:" + urlMatcher.group().toLowerCase());
        }
        
        Matcher numberMatcher = NUMBER_PATTERN.matcher(text);
        while (numberMatcher.find()) {
            specialTokens.add("num:" + numberMatcher.group());
        }
        
        return specialTokens;
    }
    
    private String replaceSpecialTokens(String text) {
        String result = EMAIL_PATTERN.matcher(text).replaceAll("_EMAIL_");
        result = URL_PATTERN.matcher(result).replaceAll("_URL_");
        result = NUMBER_PATTERN.matcher(result).replaceAll("_NUM_");
        return result;
    }
}