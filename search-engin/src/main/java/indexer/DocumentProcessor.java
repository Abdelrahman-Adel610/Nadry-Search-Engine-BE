package indexer;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class DocumentProcessor {
    private static final Logger logger = LoggerFactory.getLogger(DocumentProcessor.class);
    private final List<String> unwantedSelectors;
    private final ExecutorService executorService;

    public DocumentProcessor() {
        this(Arrays.asList("script", ".ads", ".comments"));
    }

    public DocumentProcessor(List<String> unwantedSelectors) {
        this.unwantedSelectors = unwantedSelectors != null ? unwantedSelectors : Collections.emptyList();
        this.executorService = Executors.newCachedThreadPool();
    }

    public List<ProcessedDocument> process(List<String> htmlStrings, List<String> urls) throws DocumentProcessingException {
        if (htmlStrings.size() != urls.size()) {
            throw new IllegalArgumentException("HTML strings and URLs lists must be the same size.");
        }

        logger.info("Processing {} HTML documents", htmlStrings.size());
        List<ProcessedDocument> processedDocuments = new ArrayList<>();
        
        for (int i = 0; i < htmlStrings.size(); i++) {
            String htmlContent = htmlStrings.get(i);
            String url = urls.get(i);
            try {
                if (htmlContent == null || htmlContent.trim().isEmpty()) {
                    logger.warn("Skipping empty or null HTML content for URL: {}", url);
                    throw new DocumentProcessingException("Empty or null HTML content", null);
                }
                
                byte[] contentBytes = htmlContent.getBytes("UTF-8");
                if (contentBytes.length > 100_000_000) {
                    logger.warn("HTML content exceeds size limit for URL: {}", url);
                    throw new DocumentProcessingException("Document exceeds maximum size limit", null);
                }

                Document doc = Jsoup.parse(htmlContent, url);
                String title = doc.title() != null ? doc.title() : "";
                logger.debug("Extracted title: {} for URL: {}", title, url);
                String description = doc.selectFirst("meta[name=description]") != null 
                    ? doc.selectFirst("meta[name=description]").attr("content") : "";

                Set<String> links = extractLinks(doc, url);
                doc.select(String.join(",", unwantedSelectors)).remove();
                String mainContent = extractMainContent(doc);

                String docId = generateDocId(url);
                logger.debug("Extracted {} links for URL {}: {}", links.size(), url, links);

                processedDocuments.add(new ProcessedDocument(docId, url, title, description, mainContent, "memory", links));
                
            } catch (UnsupportedEncodingException e) {
                logger.error("Encoding error while processing HTML content for URL {}: {}", url, e.getMessage());
                throw new DocumentProcessingException("Failed to process HTML content: encoding error", e);
            } catch (Exception e) {
                logger.error("Unexpected error processing HTML content for URL {}: {}", url, e.getMessage());
                throw new DocumentProcessingException("Failed to process HTML content", e);
            }
        }
        
        logger.info("Successfully processed {} documents", processedDocuments.size());
        return processedDocuments;
    }

    public CompletableFuture<List<ProcessedDocument>> processAsync(List<String> htmlStrings, List<String> urls) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return process(htmlStrings, urls);
            } catch (DocumentProcessingException e) {
                logger.error("Async processing failed: {}", e.getMessage());
                return Collections.emptyList();
            }
        }, executorService);
    }

    public List<ProcessedDocument> processDocumentsAsync(List<String> htmlStrings, List<String> urls) {
        if (htmlStrings.size() != urls.size()) {
            throw new IllegalArgumentException("HTML strings and URLs lists must be the same size.");
        }

        List<CompletableFuture<List<ProcessedDocument>>> futures = new ArrayList<>();
        // Process each HTML string individually for better parallelism
        for (int i = 0; i < htmlStrings.size(); i++) {
            final int index = i; // Create a final copy of i
            List<String> singleHtml = Collections.singletonList(htmlStrings.get(index));
            List<String> singleUrl = Collections.singletonList(urls.get(index));
            futures.add(processAsync(singleHtml, singleUrl).exceptionally(throwable -> {
                logger.error("Failed to process HTML content for URL {}: {}", urls.get(index), throwable.getMessage());
                return Collections.emptyList();
            }));
        }

        List<ProcessedDocument> results = futures.stream()
            .map(CompletableFuture::join)
            .flatMap(List::stream)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        if (results.size() < htmlStrings.size()) {
            logger.warn("Processed {}/{} documents successfully", results.size(), htmlStrings.size());
        }

        return results;
    }

    private String extractMainContent(Document doc) {
        StringBuilder contentBuilder = new StringBuilder();

        Element main = doc.selectFirst("main, article, div[class*=content], div[id*=content]");
        if (main != null) {
            contentBuilder.append(main.text()).append(" ");
        }

        List<Element> extraSections = doc.select(
            "article, section, header, footer, main, " +
            "h1, h2, h3, h4, h5, h6, " +
            "p, blockquote, pre, li, dt, dd, " +
            "a[href], strong, em, cite, q, time, code, span"
        );

        for (Element section : extraSections) {
            contentBuilder.append(section.text()).append(" ");
        }

        String content = contentBuilder.toString().trim();
        if (content.isEmpty()) {
            content = doc.body() != null ? doc.body().text() : "";
        }

        return content;
    }

    private String generateDocId(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(url.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private Set<String> extractLinks(Document doc, String baseUrl) {
        Set<String> links = new HashSet<>();
        for (Element link : doc.select("a[href]")) {
            String href = link.attr("abs:href");
            logger.debug("Processing link: {}", href);
            String normalized = normalizeUrl(href, baseUrl);
            if (normalized != null && (normalized.startsWith("http://") || normalized.startsWith("https://"))) {
                logger.debug("Normalized link: {}", normalized);
                links.add(normalized);
            } else {
                logger.debug("Skipped invalid or non-HTTP link: {}", href);
            }
        }
        return links;
    }

    private String normalizeUrl(String url, String baseUrl) {
        if (url == null || url.trim().isEmpty()) {
            logger.debug("Skipped empty URL");
            return null;
        }
        try {
            URI baseUri = new URI(baseUrl);
            String encodedUrl = url.replace(" ", "%20").replace("|", "%7C");
            URI absoluteUri = baseUri.resolve(encodedUrl);
            String normalized = absoluteUri.normalize().toString();
            if (normalized.contains("#")) {
                normalized = normalized.substring(0, normalized.indexOf('#'));
            }
            normalized = normalized.toLowerCase();
            normalized = normalized.replaceAll("(?<!https:)/+", "/");
            if (normalized.contains("?")) {
                String[] parts = normalized.split("\\?", 2);
                String query = Arrays.stream(parts[1].split("&"))
                    .filter(s -> !s.isEmpty())
                    .sorted()
                    .collect(Collectors.joining("&"));
                normalized = parts[0] + (query.isEmpty() ? "" : "?" + query);
            }
            new java.net.URL(normalized).toURI();
            return normalized;
        } catch (URISyntaxException | java.net.MalformedURLException e) {
            logger.debug("Failed to normalize URL: {}, error: {}", url, e.getMessage());
            return null;
        }
    }

    public void shutdown() {
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.error("Executor service did not terminate in time.", e);
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}