import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class IndexBuilder {
    private final DocumentProcessor docProcessor;
    private final Tokenizer tokenizer;
    private final InvertedIndex index;
    private final int numThreads;
    
    public IndexBuilder(DocumentProcessor docProcessor, Tokenizer tokenizer) {
        this(docProcessor, tokenizer, Runtime.getRuntime().availableProcessors());
    }
    
    public IndexBuilder(DocumentProcessor docProcessor, Tokenizer tokenizer, int numThreads) {
        this.docProcessor = docProcessor;
        this.tokenizer = tokenizer;
        this.index = new InvertedIndex();
        this.numThreads = numThreads;
    }
    
    /**
     * Build index from document paths and URLs
     */
    public void buildIndex(List<Path> documentPaths, List<String> urls) {
        if (documentPaths.size() != urls.size()) {
            throw new IllegalArgumentException("Document paths and URLs must be of same size");
        }
        
        System.out.println("Starting index build with " + numThreads + " threads");
        System.out.println("Total documents to process: " + documentPaths.size());
        
        // Setup for parallel processing
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        
        // Track progress
        AtomicInteger processedCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        // Process each document in parallel
        for (int i = 0; i < documentPaths.size(); i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    // Process document
                    ProcessedDocument doc = docProcessor.process(documentPaths.get(idx), urls.get(idx));
                    
                    // Print document details
                    System.out.println("Indexed document: " + doc.getDocId());
                    System.out.println("  URL: " + doc.getUrl());
                    System.out.println("  Title: " + doc.getTitle());
                    System.out.println("  Description: " + doc.getDescription());
                    System.out.println("  Content: " + doc.getContent());
                    System.out.println("  File Path: " + doc.getFilePath());
                    System.out.println("  Links: " + doc.getLinks());
                    
                    // Index fields separately with different weights
                    indexDocumentField(doc.getTitle(), doc.getDocId(), InvertedIndex.FieldType.TITLE);
                    indexDocumentField(doc.getDescription(), doc.getDocId(), InvertedIndex.FieldType.DESCRIPTION);
                    indexDocumentField(doc.getContent(), doc.getDocId(), InvertedIndex.FieldType.BODY);
                    
                    // Track success
                    successCount.incrementAndGet();
                    
                } catch (Exception e) {
                    // Log error and continue with next document
                    System.err.println("Error processing document: " + documentPaths.get(idx) + 
                                     ", Error: " + e.getMessage());
                    errorCount.incrementAndGet();
                }
                
                // Update progress
                int processed = processedCount.incrementAndGet();
                if (processed % 100 == 0 || processed == documentPaths.size()) {
                    System.out.println("Processed " + processed + "/" + documentPaths.size() + 
                                      " documents. Success: " + successCount.get() + 
                                      ", Errors: " + errorCount.get());
                }
            });
        }
        
        // Shutdown executor and wait for completion
        executor.shutdown();
        try {
            // Improved shutdown with timeout
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                System.err.println("Index building timed out, some tasks may not have completed");
            }
        } catch (InterruptedException e) {
            System.err.println("Index building interrupted: " + e.getMessage());
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // Print index summary
        System.out.println("Index building complete. Total documents indexed: " + successCount.get());
        System.out.println("Total unique terms in index: " + index.size());
    }
    
    /**
     * Index a specific field of a document
     */
    private void indexDocumentField(String text, String docId, InvertedIndex.FieldType fieldType) {
        if (text == null || text.isEmpty()) {
            return;
        }
        
        List<String> tokens = tokenizer.tokenize(text);
        for (int pos = 0; pos < tokens.size(); pos++) {
            index.addTerm(tokens.get(pos), docId, pos, fieldType);
        }
    }
}