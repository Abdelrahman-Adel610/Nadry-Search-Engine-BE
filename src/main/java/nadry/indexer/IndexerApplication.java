package nadry.indexer;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import com.mongodb.MongoWriteException;
import com.mongodb.client.*;
import com.mongodb.client.model.*;

import nadry.webCrawler.CrawlerWrapper;
import nadry.webCrawler.MongoJava;

public class IndexerApplication {
    private static final int QUEUE_CAPACITY = 1000; // Queue size to balance memory and throughput
    private static final int CONSUMER_THREADS = 15; // Number of consumer threads for indexing
    private final MongoClient mongoClient =MongoClients.create("mongodb://localhost:27017/Ndry");
    private static final MongoDatabase database2 = MongoClients.create("mongodb://localhost:27017/Ndry").getDatabase("Ndry");
    private static final String CRAWLED_URLS_COLLECTION = "crawled_html"; // Collection name for crawled documents

    public static void main(String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(16); // 1 producer + 15 consumers
        InvertedIndex index = null;
        MongoDBIndexStore mongoStore = null;
        MongoJava db = new MongoJava("mongodb://localhost:27017/", "Ndry");

//        CrawlerWrapper crawler = new CrawlerWrapper(db);
        
        // Initialize the BlockingQueue
        BlockingQueue<Document> documentQueue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);

        try {
            // MongoDB configuration
            String mongoConnectionString = System.getenv("MONGO_URI") != null
                ? System.getenv("MONGO_URI")
                : nadry.Config.DATABASE_URL;
            String databaseName = nadry.Config.DATABASE_NAME;
            String collectionName = nadry.Config.INVERTED_INDEX_COLLECTION_NAME;

            // Initialize components
            DocumentProcessor processor = new DocumentProcessor();
            StopWordFilter stopWordFilter = new StopWordFilter();
            Tokenizer tokenizer = new Tokenizer(stopWordFilter);
            mongoStore = new MongoDBIndexStore(mongoConnectionString, databaseName, collectionName);

            // Clear collections
            MongoDatabase database = mongoStore.getDatabase();
            database.getCollection(collectionName).drop();
            database.getCollection(nadry.Config.DOCUMENTS_COLLECTION_NAME).drop();
            System.out.println("Cleared inverted_index and Documents collections");

            // Initialize IndexBuilder
            IndexBuilder indexBuilder = new IndexBuilder(
                processor,
                tokenizer,
                mongoConnectionString,
                databaseName,
                collectionName
            );
            index = getIndex(indexBuilder);

            // Start producer thread to fetch documents one by one
            CompletableFuture<Void> producerFuture = CompletableFuture.runAsync(() -> {
                MongoCursor<Document> cursor = null;
                try {
                    MongoCollection<Document> collection = database2.getCollection(CRAWLED_URLS_COLLECTION);
                    cursor = collection.find().iterator();
                    int documentCount = 0;
                    while (cursor.hasNext()) {
                        Document doc = cursor.next();
                        documentQueue.put(doc); // Add document to queue, blocks if full
                        documentCount++;
                    }
                    System.out.println("Producer retrieved and queued " + documentCount + " documents.");
                    System.out.println("Producer finished adding documents to queue.");
                } catch (InterruptedException e) {
                    System.err.println("Producer interrupted: " + e.getMessage());
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    System.err.println("Producer error: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    // Close cursor
                    if (cursor != null) {
                        cursor.close();
                    }
                    // Add poison pill to signal consumers to stop
                    try {
                        documentQueue.put(new Document("poison", "pill"));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }, executor);

            // Start consumer threads to process documents from queue
            List<CompletableFuture<Void>> consumerFutures = new ArrayList<>();
            for (int i = 0; i < CONSUMER_THREADS; i++) {
                CompletableFuture<Void> consumerFuture = CompletableFuture.runAsync(() -> {
                    try {
                        while (true) {
                            Document doc = documentQueue.take(); // Blocks until document is available
                            if (doc.containsKey("poison") && doc.getString("poison").equals("pill")) {
                                // Re-add poison pill for other consumers
                                documentQueue.put(doc);
                                break; // Exit consumer loop
                            }
                            // Process document
                            String html = doc.getString("html");
                            String url = doc.getString("url");
                            indexBuilder.buildIndex(List.of(html), List.of(url));
                        }
                        System.out.println("Consumer thread finished.");
                    } catch (InterruptedException e) {
                        System.err.println("Consumer interrupted: " + e.getMessage());
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        System.err.println("Consumer error: " + e.getMessage());
                        e.printStackTrace();
                    }
                }, executor);
                consumerFutures.add(consumerFuture);
            }

            // Wait for producer and consumers to complete
            producerFuture.join();
            CompletableFuture.allOf(consumerFutures.toArray(new CompletableFuture[0])).join();
            System.out.println("Index building completed.");

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
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
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