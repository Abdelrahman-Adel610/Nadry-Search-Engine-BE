package indexer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;

import webCrawler.WebCrawler;

public class MultiThreadedIndexer {
    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);  // Adjust the number of threads based on your needs

        try {
            String urlSeed = "https://en.wikipedia.org/wiki/Wiki";
            //WebCrawler crawler = new WebCrawler(urlSeed);
            //crawler.start();
            
            // Define document paths and URLs
            Path filePath = Paths.get("src/main/java/test.html");
            Path filePath2 = Paths.get("src/main/java/sample1.html");
            // Use unique URLs for each file
            // hi aref
            String url1 = "https://example.com/test.html";
            String url2 = "https://example.com/sample1.html";

            if (!Files.exists(filePath) || !Files.exists(filePath2)) {
                throw new DocumentProcessingException("One or more files do not exist", null);
            }

            List<Path> paths = new LinkedList<>();
            List<String> links = new LinkedList<>();
            paths.add(filePath);
            paths.add(filePath2);
            links.add(url1);
            links.add(url2);

            // Initialize components
            DocumentProcessor processor = new DocumentProcessor();
            StopWordFilter stopWordFilter = new StopWordFilter();
            Tokenizer tokenizer = new Tokenizer(stopWordFilter);
            IndexBuilder indexBuilder = new IndexBuilder(processor, tokenizer);

            // Print input for debugging
            System.out.println("URLs:");
            for (String link : links) {
                System.out.println(link);
            }
            System.out.println("--------------------------------------");
            System.out.println("Document Paths:");
            for (Path path : paths) {
                System.out.println(path.toString());
            }
            System.out.println("--------------------------------------");

            // Submit the indexing task to the executor
            CompletableFuture<Void> indexBuildingFuture = CompletableFuture.runAsync(() -> {
                try {
                    indexBuilder.buildIndex(paths, links);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, executor);

            // Wait for index building to complete
            indexBuildingFuture.join();

            // Query and verify index concurrently
            CompletableFuture<Void> verifyIndexFuture = CompletableFuture.runAsync(() -> {
                try {
                    // Accessing the inverted index using reflection
                    InvertedIndex index = getIndex(indexBuilder);
                    System.out.println("\nVerifying InvertedIndex Contents:");
                    System.out.println("Total unique terms: " + index.size());

                    // Print all terms and their postings sequentially to avoid console interleaving
                    index.getTerms().stream().forEach(term -> {
                        List<InvertedIndex.Posting> postings = index.getPostings(term);
                        System.out.println("Term: " + term);
                        for (InvertedIndex.Posting posting : postings) {
                            System.out.println("  DocID: " + posting.getDocId());
                            System.out.println("  Frequency: " + posting.getFrequency());
                            System.out.println("  Weight: " + posting.getWeight());
                            System.out.println("  Field Types: " + posting.getFieldTypes());
                            System.out.println("  Positions (TITLE): " + posting.getPositions(InvertedIndex.FieldType.TITLE));
                            System.out.println("  Positions (DESCRIPTION): " + posting.getPositions(InvertedIndex.FieldType.DESCRIPTION));
                            System.out.println("  Positions (BODY): " + posting.getPositions(InvertedIndex.FieldType.BODY));
                        }
                    });

                    // Verify specific tokens sequentially
                    System.out.println("\nVerifying Specific Tokens:");
                    String[] testTerms = {"main","menu" , "aref"};
                    for (String term : testTerms) {
                        List<InvertedIndex.Posting> postings = index.getPostings(term);
                        if (postings.isEmpty()) {
                            System.out.println("ERROR: Term '" + term + "' not found in index");
                        } else {
                            System.out.println("Term '" + term + "' found with " + postings.size() + " postings");
                            postings.forEach(posting -> {
                                System.out.println("  DocID: " + posting.getDocId() + ", Frequency: " + posting.getFrequency());
                            });
                        }
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, executor);

            // Wait for the verification task to complete
            verifyIndexFuture.join();

        } catch (DocumentProcessingException e) {
            System.err.println("Error during indexing: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Shut down the executor
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    // Helper method to access private index field (since InvertedIndex is not modifiable)
    private static InvertedIndex getIndex(IndexBuilder builder) throws Exception {
        java.lang.reflect.Field indexField = IndexBuilder.class.getDeclaredField("index");
        indexField.setAccessible(true);
        return (InvertedIndex) indexField.get(builder);
    }
}