package indexer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        InvertedIndex index = null;

        try {
            // Define document paths and URLs
            Path filePath = Paths.get("src/main/java/test.html");
            Path filePath2 = Paths.get("src/main/java/sample1.html");
            Path filePath3 = Paths.get("src/main/java/sample2.html");
            String url1 = "https://example.com/test.html";
            String url2 = "https://example.com/sample1.html";
            String url3 = "https://example.com/sample2.html";

            // Validate file existence
            if (!Files.exists(filePath) || !Files.exists(filePath2)) {
                throw new RuntimeException("One or more files do not exist");
            }

            List<Path> paths = new LinkedList<>();
            List<String> links = new LinkedList<>();
            paths.add(filePath);
            paths.add(filePath2);
            paths.add(filePath3);
            links.add(url1);
            links.add(url2);
            links.add(url3);

            // MongoDB configuration
            String mongoConnectionString = "mongodb://localhost:27017/search_engine";
            String databaseName = "search_engine";
            String collectionName = "inverted_index";

            // Initialize components outside the future
            DocumentProcessor processor = new DocumentProcessor();
            StopWordFilter stopWordFilter = new StopWordFilter();
            Tokenizer tokenizer = new Tokenizer(stopWordFilter);

            // Print input
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

            // Submit index building task
            System.out.println("Starting index building...");
            IndexBuilder indexBuilder = new IndexBuilder(
                processor,
                tokenizer,
                mongoConnectionString,
                databaseName,
                collectionName
            );
            index = getIndex(indexBuilder); // Store index for testing
            CompletableFuture<Void> indexBuildingFuture = CompletableFuture.runAsync(() -> {
                try {
                    indexBuilder.buildIndex(paths, links);
                    System.out.println("Index building completed.");
                } catch (Exception e) {
                    System.err.println("Error during index building: " + e.getMessage());
                    e.printStackTrace();
                    throw new RuntimeException("Index building failed", e);
                }
            }, executor);

            // Wait for index building
            indexBuildingFuture.join();
            index.close()	;
            executor.shutdown();

//            // Test the inverted index
//            System.out.println("Testing inverted index...");
//            CompletableFuture<Void> testFuture = CompletableFuture.runAsync(() -> {
//                try {
//                    System.out.println("Total unique terms: " + index.size());
//
//                    String[] testTerms = {"main", "menu", "aref"};
//                    for (String term : testTerms) {
//                        List<InvertedIndex.Posting> postings = index.getPostings(term);
//                        if (postings.isEmpty()) {
//                            System.out.println("ERROR: Term '" + term + "' not found in index");
//                        } else {
//                            System.out.println("Term '" + term + "' found with " + postings.size() + " postings:");
//                            for (InvertedIndex.Posting posting : postings) {
//                                System.out.println("  DocID: " + posting.getDocId());
//                                System.out.println("    Frequency: " + posting.getFrequency());
//                                System.out.println("    Weight: " + posting.getWeight());
//                                System.out.println("    Field Types: " + posting.getFieldTypes());
//                                for (InvertedIndex.FieldType fieldType : posting.getFieldTypes()) {
//                                    System.out.println("    Positions (" + fieldType + "): " + posting.getPositions(fieldType));
//                                }
//                            }
//                        }
//                    }
//                    System.out.println("Index testing completed.");
//                } catch (Exception e) {
//                    System.err.println("Error during index testing: " + e.getMessage());
//                    e.printStackTrace();
//                }
//            }, executor);
//
//            // Wait for testing
//            testFuture.join();

        } catch (Exception e) {
            System.err.println("Error in main: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Close the index
            if (index != null) {
                try {
                    index.close();
                } catch (Exception e) {
                    System.err.println("Error closing index: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            // Shutdown executor
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    System.err.println("Executor terminate within timeout----");
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
                System.err.println("Executor shutdown interrupted: " + e.getMessage());
            }
        }
    }

    private static InvertedIndex getIndex(IndexBuilder builder) throws Exception {
        java.lang.reflect.Field indexField = IndexBuilder.class.getDeclaredField("index");
        indexField.setAccessible(true);
        return (InvertedIndex) indexField.get(builder);
    }
}