package nadry.webCrawler;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.Date;

import com.mongodb.MongoWriteException;
import com.mongodb.client.*;
import com.mongodb.client.model.*;
import org.bson.Document;

public class MongoJava {
    private static final String VISITED_URLS_COLLECTION = "visited_urls";
    private static final String QUEUED_URLS_COLLECTION = "queued_urls";
    private static final String COMPACT_STRING_COLLECTION = "compact_string";
    private static final String CRAWLED_COUNT_COLLECTION = "crawled_count";
    private static final String CRAWLED_URLS_COLLECTION = "crawled_html"; 
    
    private final MongoClient mongoClient;
    private final MongoDatabase database;
    
    // Cache to be used while being Crawling-active
    private Set<String> visitedUrlsCache = new HashSet<String>();
    private Set<String> compactStringCache = new HashSet<String>();
    
    public MongoJava(String connectionString, String dbName) {
        this.mongoClient = MongoClients.create(connectionString);
        this.database = mongoClient.getDatabase(dbName);
        initCollections();
        loadCache();
    }
    
    private void initCollections() {
        MongoCollection<Document> visitedCollection = database.getCollection(VISITED_URLS_COLLECTION);
        MongoCollection<Document> queuedCollection = database.getCollection(QUEUED_URLS_COLLECTION);
        MongoCollection<Document> compactCollection = database.getCollection(COMPACT_STRING_COLLECTION);
        MongoCollection<Document> countCollection = database.getCollection(CRAWLED_COUNT_COLLECTION);
        MongoCollection<Document> crawledCollection = database.getCollection(CRAWLED_URLS_COLLECTION); // New collection
        
        // Create indexes for queued_urls
        queuedCollection.createIndex(Indexes.ascending("url"), new IndexOptions().unique(true));
        queuedCollection.createIndex(Indexes.ascending("addedTimestamp"));
        
        // Create index for crawled_urls
        crawledCollection.createIndex(Indexes.ascending("url"), new IndexOptions().unique(true));
        
        System.out.println("Collections got initialized");
    }
    
    private void loadCache() {
        // Load visited URLs into cache
        MongoCollection<Document> visitedColl = database.getCollection(VISITED_URLS_COLLECTION);
        visitedUrlsCache.clear(); // Clear cache before loading
        MongoCursor<Document> cursor = visitedColl.find().projection(Projections.include("_id")).iterator();
        while (cursor.hasNext()) {
            visitedUrlsCache.add(cursor.next().getString("_id"));
        }
        System.out.println("Loaded " + visitedUrlsCache.size() + " visited URLs from MongoDB.");

        // Load signatures into cache
        MongoCollection<Document> sigsColl = database.getCollection(COMPACT_STRING_COLLECTION);
        compactStringCache.clear(); // Clear cache before loading
        cursor = sigsColl.find().projection(Projections.include("_id")).iterator();
        while (cursor.hasNext()) {
            compactStringCache.add(cursor.next().getString("_id"));
        }
        System.out.println("Loaded " + compactStringCache.size() + " Compact Strings from MongoDB.");

        // Check queued size
        long queuedSize = database.getCollection(QUEUED_URLS_COLLECTION).countDocuments();
        System.out.println("Queued urls size in MongoDB: " + queuedSize);
    }
    
    public boolean isVisited(String url) {
        return visitedUrlsCache.contains(url);
    }
    
    public boolean hasCompactString(String url) {
        return compactStringCache.contains(url);
    }
    
    public void markVisited(String url) {
        boolean addedToCache = visitedUrlsCache.add(url);
        if (addedToCache) { 
            try {
                MongoCollection<Document> collection = database.getCollection(VISITED_URLS_COLLECTION);
                collection.updateOne(
                        Filters.eq("_id", url),
                        Updates.set("_id", url),
                        new UpdateOptions().upsert(true)
                );
            } catch (Exception e) {
                System.err.println("Error saving visited URL " + url + " to MongoDB: " + e.getMessage());
                visitedUrlsCache.remove(url); // rollback cache on DB error
            } 
        }
    }

    public void addCompactString(String cs) {
        boolean addedToCache = compactStringCache.add(cs);
        if (addedToCache) {
            try {
                MongoCollection<Document> collection = database.getCollection(COMPACT_STRING_COLLECTION);
                collection.updateOne(
                        Filters.eq("_id", cs),
                        Updates.set("_id", cs),
                        new UpdateOptions().upsert(true)
                );
            } catch (Exception e) {
                System.err.println("Error saving Compact String " + cs + " to MongoDB: " + e.getMessage());
                compactStringCache.remove(cs); // rollback cache on DB error
            }
        }
    }

    public void enqueueUrl(String url) {
        try {
            MongoCollection<Document> collection = database.getCollection(QUEUED_URLS_COLLECTION);
            collection.updateOne(
                    Filters.eq("url", url),	
                    Updates.combine(
                        Updates.setOnInsert("url", url),
                        Updates.setOnInsert("addedTimestamp", new Date())
                    ),
                    new UpdateOptions().upsert(true)
            );
        } catch (Exception e) {
            System.err.println("Error enqueuing URL " + url + " to MongoDB: " + e.getMessage());
        } 
    }

    public String dequeueUrl() {
        try {
            MongoCollection<Document> collection = database.getCollection(QUEUED_URLS_COLLECTION);
            Document dequeuedDoc = collection.findOneAndDelete(
                    new Document(), // Empty filter
                    new FindOneAndDeleteOptions().sort(Sorts.ascending("addedTimestamp"))
            );
            return (dequeuedDoc != null) ? dequeuedDoc.getString("url") : null;
        } catch (Exception e) {
            System.err.println("Error dequeuing URL from MongoDB: " + e.getMessage());
            return null;
        }
    }
    
    public long getQueueCount() {
        try {
            MongoCollection<Document> collection = database.getCollection(QUEUED_URLS_COLLECTION);
            return collection.countDocuments();
        } catch (Exception e) {
            System.err.println("Error getting MongoDB queue count: " + e.getMessage());
            return -1; 
        }
    }
    
    public int getCrawledCount() {
        try {
            MongoCollection<Document> collection = database.getCollection(CRAWLED_COUNT_COLLECTION);
            Document counterDoc = collection.find(Filters.eq("_id", "page_counter")).first();
            if (counterDoc != null) {
                return counterDoc.getInteger("count", 0);
            } else {
                return 0;
            }	 	
        } catch(Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    public int incrementAndGetCrawledCount() {
        try {
            MongoCollection<Document> collection = database.getCollection(CRAWLED_COUNT_COLLECTION);
            FindOneAndUpdateOptions options = new FindOneAndUpdateOptions()
                    .upsert(true)
                    .returnDocument(ReturnDocument.AFTER);
            Document updatedDoc = collection.findOneAndUpdate(
                Filters.eq("_id", "page_counter"),
                Updates.inc("count", 1),
                options
            );
            if (updatedDoc != null) {
                Number newCount = updatedDoc.get("count", Number.class);
                return (newCount != null) ? newCount.intValue() : 0;
            } else {
                System.err.println("Failed to update/create counter document, findOneAndUpdate returned null unexpectedly.");
                return -1;
            }
        } catch (Exception e) {
            System.err.println("Error incrementing and getting crawled count in MongoDB: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }

    public boolean isQueueEmpty() {
        try {
            MongoCollection<Document> collection = database.getCollection(QUEUED_URLS_COLLECTION);
            return collection.countDocuments() == 0;
        } catch (Exception e) {
            System.err.println("Error checking MongoDB queue empty: " + e.getMessage());
            return true; 
        }
    }

    public void addCrawledPage(String url, String html) {
        try {
            MongoCollection<Document> collection = database.getCollection(CRAWLED_URLS_COLLECTION);

            Document doc = new Document("url", url) // Use the url passed as parameter
                                .append("html", html)
                                .append("crawledTimestamp", new Date());

            collection.insertOne(doc);
        } catch (Exception e) {
            System.err.println("Error adding crawled page for " + url + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    public List<Document> getAllDocuments() {
        List<Document> documents = new ArrayList<>();
        MongoCollection<Document> collection = database.getCollection(CRAWLED_URLS_COLLECTION);
        MongoCursor<Document> cursor = null; // Declare outside try for finally block
        try {
            // find() with no filter retrieves all documents
            cursor = collection.find().iterator();
            // Iterate through the cursor and add each document to the list
            while (cursor.hasNext()) {
                documents.add(cursor.next());
            }
            System.out.println("Retrieved " + documents.size() + " documents.");

        } catch (Exception e) {
            e.printStackTrace();
            documents.clear(); // Clear the list in case of an error
        } finally {
            // release resources
            if (cursor != null) {
                cursor.close();
            }
        }
        return documents;
    }

    public void close() {
        if (mongoClient != null) {
            mongoClient.close();
            System.out.println("MongoDB connection closed.");
        }
    }
}