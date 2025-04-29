package indexer;
import org.apache.tika.Tika;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
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
        // Changed to cached thread pool for I/O-bound tasks
        this.executorService = Executors.newCachedThreadPool();
    }

    public ProcessedDocument process(Path filePath, String url) throws DocumentProcessingException {
        logger.info("Processing file: {}", filePath);
        try {
            if (!Files.isRegularFile(filePath)) {
                throw new DocumentProcessingException("File not found or not a regular file: " + filePath, null);
            }
            if (Files.size(filePath) > 100_000_000) {
                throw new DocumentProcessingException("Document exceeds maximum size limit", null);
            }
            String contentType = detectContentType(filePath);
            if (!isValidHtml(contentType)) {
                throw new DocumentProcessingException("Invalid content type: " + contentType, null);
            }
            try (InputStream input = Files.newInputStream(filePath)) {
                Document doc = Jsoup.parse(input, "UTF-8", url);
                String title = doc.title();
                logger.debug("Extracted title: {}", title);
                String description = doc.selectFirst("meta[name=description]") != null 
                    ? doc.selectFirst("meta[name=description]").attr("content") : "";

                Set<String> links = extractLinks(doc, url);
                doc.select(String.join(",", unwantedSelectors)).remove();
                String mainContent = extractMainContent(doc);

                String docId = generateDocId(url);
                logger.debug("Extracted {} links: {}", links.size(), links);

                return new ProcessedDocument(docId, url, title, description, mainContent, filePath.toString(), links);
            }
        } catch (IOException e) {
            logger.error("Failed to process document: {}", filePath, e);
            throw new DocumentProcessingException("Failed to process document: " + filePath, e);
        }
    }

    public CompletableFuture<ProcessedDocument> processAsync(Path filePath, String url) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return process(filePath, url);
            } catch (DocumentProcessingException e) {
                logger.error("Async processing failed for {}: {}", filePath, e.getMessage());
                return null;
            }
        }, executorService);
    }

    public List<ProcessedDocument> processDocumentsAsync(List<Path> paths, List<String> urls) {
        if (paths.size() != urls.size()) {
            throw new IllegalArgumentException("Paths and URLs lists must be the same size.");
        }

        List<CompletableFuture<ProcessedDocument>> futures = new ArrayList<>();
        for (int i = 0; i < paths.size(); i++) {
            Path path = paths.get(i);
            String url = urls.get(i);
            // Enhanced error handling with exceptional completion
            futures.add(processAsync(path, url).exceptionally(throwable -> {
                logger.error("Failed to process {}: {}", path, throwable.getMessage());
                return null;
            }));
        }

        List<ProcessedDocument> results = futures.stream()
            .map(CompletableFuture::join)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        // Log summary of processing
        if (results.size() < paths.size()) {
            logger.warn("Processed {}/{} documents successfully", results.size(), paths.size());
        }

        return results;
    }

    private String extractMainContent(Document doc) {
        StringBuilder contentBuilder = new StringBuilder();

        // Include main article/content
        Element main = doc.selectFirst("main, article, div[class*=content], div[id*=content]");
        if (main != null) {
            contentBuilder.append(main.text()).append(" ");
        }

        // Include semantic structure if available
        List<Element> extraSections = doc.select(
            "article, section, header, footer, main, " +
            "h1, h2, h3, h4, h5, h6, " +
            "p, blockquote, pre, li, dt, dd, " +
            "a[href], strong, em, cite, q, time, code, span"
        );

        for (Element section : extraSections) {
            contentBuilder.append(section.text()).append(" ");
        }

        // Fallback to full body if nothing found
        if (contentBuilder.toString().trim().isEmpty()) {
            return doc.body().text();
        }

        return contentBuilder.toString().trim();
    }

    private String detectContentType(Path filePath) throws IOException {
        // Create new Tika instance per call to ensure thread safety
        Tika tika = new Tika();
        return tika.detect(filePath.toFile());
    }

    private boolean isValidHtml(String contentType) {
        return "text/html".equals(contentType);
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
            new java.net.URL(normalized).toURI(); // Validate URL
            return normalized;
        } catch (URISyntaxException | java.net.MalformedURLException e) {
            logger.debug("Failed to normalize URL: {}, error: {}", url, e.getMessage());
            return null;
        }
    }

    public void shutdown() {
        try {
            // Improved shutdown with timeout
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