package indexer;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class IndexBuilder {
    private final DocumentProcessor docProcessor;
    private final Tokenizer tokenizer;
    private final InvertedIndex index;
    private final int numThreads;

    public IndexBuilder(DocumentProcessor docProcessor, Tokenizer tokenizer,
                        String mongoConnectionString, String databaseName, String collectionName) {
        this(docProcessor, tokenizer, Runtime.getRuntime().availableProcessors(),
             mongoConnectionString, databaseName, collectionName);
    }

    public IndexBuilder(DocumentProcessor docProcessor, Tokenizer tokenizer, int numThreads,
                        String mongoConnectionString, String databaseName, String collectionName) {
        if (docProcessor == null || tokenizer == null) {
            throw new IllegalArgumentException("DocumentProcessor and Tokenizer cannot be null");
        }
        this.docProcessor = docProcessor;
        this.tokenizer = tokenizer;
        this.index = new InvertedIndex(mongoConnectionString, databaseName, collectionName);
        this.numThreads = numThreads;
    }

    public void buildIndex(List<Path> documentPaths, List<String> urls) {
        if (documentPaths.size() != urls.size()) {
            throw new IllegalArgumentException("Document paths and URLs must be of same size");
        }

        System.out.println("Starting index build with " + numThreads + " threads");
        System.out.println("Total documents to process: " + documentPaths.size());

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        AtomicInteger processedCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < documentPaths.size(); i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    long startTime = System.currentTimeMillis();
                    System.out.println("Processing document: " + documentPaths.get(idx));
                    ProcessedDocument doc = docProcessor.process(documentPaths.get(idx), urls.get(idx));

                    System.out.println("Processed document: " + doc.getDocId() + " in " +
                                      (System.currentTimeMillis() - startTime) + "ms");
                    System.out.println("  URL: " + doc.getUrl());
                    System.out.println("  Title: " + doc.getTitle());
                    System.out.println("  Description: " + doc.getDescription());
                    System.out.println("  Content: " + doc.getContent());
                    System.out.println("  File Path: " + doc.getFilePath());
                    System.out.println("  Links: " + doc.getLinks());

                    startTime = System.currentTimeMillis();
                    indexDocumentField(doc.getTitle(), doc.getDocId(), InvertedIndex.FieldType.TITLE);
                    indexDocumentField(doc.getDescription(), doc.getDocId(), InvertedIndex.FieldType.DESCRIPTION);
                    indexDocumentField(doc.getContent(), doc.getDocId(), InvertedIndex.FieldType.BODY);
                    System.out.println("Indexed document " + doc.getDocId() + " in " +
                                      (System.currentTimeMillis() - startTime) + "ms");

                    successCount.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("Error processing document: " + documentPaths.get(idx) +
                                     ", Error: " + e.getMessage());
                    e.printStackTrace();
                    errorCount.incrementAndGet();
                }

                int processed = processedCount.incrementAndGet();
                System.out.println("Processed " + processed + "/" + documentPaths.size() +
                                  " documents. Success: " + successCount.get() +
                                  ", Errors: " + errorCount.get());
            });
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                System.err.println("Index building executor did not terminate within timeout");
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            System.err.println("Index building interrupted: " + e.getMessage());
        }

        System.out.println("Index building complete. Total documents indexed: " + successCount.get());
        System.out.println("Total unique terms in index: " + index.getTerms().size());

    }

    private void indexDocumentField(String text, String docId, InvertedIndex.FieldType fieldType) {
        if (text == null || text.isEmpty()) {
            return;
        }

        List<String> tokens = tokenizer.tokenize(text);
        Map<String, InvertedIndex.Posting> termPostings = new HashMap<>();
        
        // Create postings for each token
        for (int pos = 0; pos < tokens.size(); pos++) {
            String term = tokens.get(pos);
            InvertedIndex.Posting posting = termPostings.computeIfAbsent(term, k -> new InvertedIndex.Posting(docId));
            posting.addPosition(pos, fieldType);
        }

        // Add postings to index
        termPostings.forEach((term, posting) -> index.addTerm(term, posting));
    }
}