package indexer;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class IndexBuilder {
    private final DocumentProcessor docProcessor;
    private final Tokenizer tokenizer;
    private final InvertedIndex index;
    private final MongoDBIndexStore mongoStore;
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
        this.mongoStore = new MongoDBIndexStore(mongoConnectionString, databaseName, collectionName);
        this.index = new InvertedIndex(mongoConnectionString, databaseName, collectionName);
        this.numThreads = numThreads;
    }

    public InvertedIndex getIndex() {
        return index;
    }

    public void buildIndex(List<String> htmlStrings, List<String> urls) {
        if (htmlStrings.size() != urls.size()) {
            throw new IllegalArgumentException("HTML strings and URLs must be of same size");
        }

        System.out.println("Starting index build with " + numThreads + " threads");
        System.out.println("Total documents to process: " + htmlStrings.size());

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        AtomicInteger processedCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < htmlStrings.size(); i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    long startTime = System.currentTimeMillis();
                    System.out.println("Processing document #" + idx + " for URL: " + urls.get(idx));
                    List<ProcessedDocument> docs = docProcessor.process(
                        Collections.singletonList(htmlStrings.get(idx)), 
                        Collections.singletonList(urls.get(idx))
                    );

                    for (ProcessedDocument doc : docs) {
                        System.out.println("Processed document: " + doc.getDocId() + " in " +
                                        (System.currentTimeMillis() - startTime) + "ms");
                        System.out.println("  URL: " + doc.getUrl());
                        System.out.println("  Title: " + doc.getTitle());
                        System.out.println("  Description: " + doc.getDescription());
                        System.out.println("  Content: " + doc.getContent());
                        System.out.println("  Source: " + doc.getFilePath());
                        System.out.println("  Links: " + doc.getLinks());

                        startTime = System.currentTimeMillis();
                        int totalWords = 0;
                        totalWords += indexDocumentField(doc.getTitle(), doc.getDocId(), urls.get(idx), InvertedIndex.FieldType.TITLE);
                        totalWords += indexDocumentField(doc.getDescription(), doc.getDocId(), urls.get(idx), InvertedIndex.FieldType.DESCRIPTION);
                        totalWords += indexDocumentField(doc.getContent(), doc.getDocId(), urls.get(idx), InvertedIndex.FieldType.BODY);
                        System.out.println("Indexed document " + doc.getDocId() + " in " +
                                        (System.currentTimeMillis() - startTime) + "ms");

                        List<String> linksList = new ArrayList<>(doc.getLinks());
                        System.out.println("Converted links: " + doc.getLinks() + " -> " + linksList);

                        System.out.println("Saving document: " + doc.getDocId() + " with " + totalWords + " tokens");
                        mongoStore.saveDocument(
                            doc.getDocId(),
                            doc.getUrl(),
                            doc.getTitle(),
                            doc.getDescription(),
                            doc.getContent(),
                            linksList,
                            totalWords
                        );
                        System.out.println("Saved document " + doc.getDocId() + " to Documents collection");

                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    System.err.println("Error processing document #" + idx + " for URL " + urls.get(idx) +
                                    ", Error: " + e.getMessage());
                    e.printStackTrace();
                    errorCount.incrementAndGet();
                }

                int processed = processedCount.incrementAndGet();
                System.out.println("Processed " + processed + "/" + htmlStrings.size() +
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

    private int indexDocumentField(String text, String docId, String url, InvertedIndex.FieldType fieldType) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        List<String> tokens = tokenizer.tokenize(text);
        System.out.println("Tokens for " + docId + " (" + fieldType + "): " + tokens);
        Map<String, InvertedIndex.Posting> termPostings = new HashMap<>();
        
        for (int pos = 0; pos < tokens.size(); pos++) {
            String term = tokens.get(pos);
            InvertedIndex.Posting posting = termPostings.computeIfAbsent(term, k -> new InvertedIndex.Posting(docId, url));
            posting.addPosition(pos, fieldType);
        }

        termPostings.forEach((term, posting) -> {
            index.addTerm(term, posting);
        });
        return tokens.size();
    }
}