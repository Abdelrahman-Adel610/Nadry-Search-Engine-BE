package indexer;

import com.mongodb.client.MongoDatabase;

import webCrawler.CrawlerWrapper;
import webCrawler.MongoJava;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.security.NoSuchAlgorithmException;

public class Main {
    public static void main(String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(16);
        InvertedIndex index = null;
        MongoDBIndexStore mongoStore = null;
        MongoJava db = new MongoJava("mongodb://localhost:27017/","Ndry");
        CrawlerWrapper crawler = new CrawlerWrapper(db);
        try {
            // Define HTML strings and URLs
            
            List<String> htmls = new LinkedList<>(crawler.getCrawledHtml());
            List<String> urls = new LinkedList<>(crawler.getCrawledUrls());

            // MongoDB configuration
            String mongoConnectionString = System.getenv("MONGO_URI") != null
                ? System.getenv("MONGO_URI")
                : "mongodb://localhost:27017/search_engine2";
            String databaseName = "search_engine2";
            String collectionName = "inverted_index";

            // Initialize components
            DocumentProcessor processor = new DocumentProcessor();
            StopWordFilter stopWordFilter = new StopWordFilter();
            Tokenizer tokenizer = new Tokenizer(stopWordFilter);
            mongoStore = new MongoDBIndexStore(mongoConnectionString, databaseName, collectionName);

            // Clear collections
            MongoDatabase database = mongoStore.getDatabase();
            database.getCollection("inverted_index").drop();
            database.getCollection("Documents").drop();
            System.out.println("Cleared inverted_index and Documents collections");

            // Submit index building task
            System.out.println("Starting index building...");
            IndexBuilder indexBuilder = new IndexBuilder(
                processor,
                tokenizer,
                mongoConnectionString,
                databaseName,
                collectionName
            );
            index = getIndex(indexBuilder);
            CompletableFuture<Void> indexBuildingFuture = CompletableFuture.runAsync(() -> {
                try {
                    indexBuilder.buildIndex(htmls, urls);
                    System.out.println("Index building completed.");
                } catch (Exception e) {
                    System.err.println("Error during index building: " + e.getMessage());
                    e.printStackTrace();
                    throw new RuntimeException("Index building failed", e);
                }
            }, executor);

            // Wait for index building
            indexBuildingFuture.join();


        } catch (Exception e) {
            System.err.println("Error in main: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Close resources
            if (index != null) {
                try {
                    index.close();
                } catch (Exception e) {
                    System.err.println("Error closing index: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            if (mongoStore != null) {
                try {
                    mongoStore.close();
                } catch (Exception e) {
                    System.err.println("Error closing MongoDB store: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            try {
                executor.shutdown();
                if (!executor.awaitTermination(60000, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    System.err.println("Executor did not terminate within timeout");
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

    private static String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
