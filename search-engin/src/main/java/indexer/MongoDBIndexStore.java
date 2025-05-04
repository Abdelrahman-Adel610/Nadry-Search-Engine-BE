package indexer;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCursor; // Add this import for MongoCursor
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.client.model.UpdateOneModel;
import org.bson.Document;
import org.bson.conversions.Bson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import indexer.InvertedIndex.FieldType;
import indexer.InvertedIndex.Posting;
import nadry.ranker.QueryDocument;

public class MongoDBIndexStore {
    private final MongoClient mongoClient;
    private final MongoDatabase database;
    private final MongoCollection<Document> collection;
    private final MongoCollection<Document> documentsCollection;
    private static final String DOCUMENTS_COLLECTION_NAME = "Documents"; // Define collection name

    public MongoDBIndexStore(String connectionString, String databaseName, String collectionName) {
        if (connectionString == null || connectionString.isEmpty()) {
            throw new IllegalArgumentException("Connection string cannot be null or empty");
        }
        if (databaseName == null || databaseName.isEmpty() || databaseName.contains("/")) {
            throw new IllegalArgumentException("Invalid database name: " + databaseName);
        }
        if (collectionName == null || collectionName.isEmpty()) {
            throw new IllegalArgumentException("Collection name cannot be null or empty");
        }
        try {
            System.out.println("Connecting to MongoDB with connection string: " + connectionString);
            ConnectionString connString = new ConnectionString(connectionString + "?connectTimeoutMS=500000&socketTimeoutMS=500000&maxPoolSize=50");
            MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(connString)
                .retryWrites(true)
                .build();
            mongoClient = MongoClients.create(settings);
            database = mongoClient.getDatabase(databaseName);
            collection = database.getCollection(collectionName);
            documentsCollection = database.getCollection("Documents");
            database.runCommand(new Document("ping", 1));
            System.out.println("Successfully connected to MongoDB database: " + databaseName + ", collection: " + collectionName);
        } catch (Exception e) {
            System.err.println("Failed to initialize MongoDB client: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("MongoDB initialization failed", e);
        }
    }

    public MongoDatabase getDatabase() {
        return database;
    }

    public DocumentData getDocument(String docId) {
        checkClientState();
        try {
            System.out.println("Retrieving document from Documents collection: " + docId);
            Document doc = documentsCollection.find(Filters.eq("_id", docId)).first();
            if (doc == null) {
                System.out.println("Document not found: " + docId);
                return null;
            }
            DocumentData documentData = new DocumentData(
                doc.getString("_id"),
                doc.getString("url"),
                doc.getString("title"),
                doc.getString("description"),
                doc.getString("content"),
                doc.getList("links", String.class),
                doc.getInteger("totalWords"),
                doc.getDouble("popularity_score")
            );
            System.out.println("Retrieved document: " + docId);
            return documentData;
        } catch (Exception e) {
            System.err.println("MongoDB error retrieving document " + docId + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public void updateDocumentScores(Map<String, Double> scoresMap) {
        checkClientState();
        try {
            System.out.println("Updating document scores...");
            for (Map.Entry<String, Double> entry : scoresMap.entrySet()) {
                String docId = entry.getKey();
                double score = entry.getValue();

                UpdateResult result = documentsCollection.updateOne(
                    Filters.eq("url", docId),
                    Updates.set("popularity_score", score)
                );

                if (result.getMatchedCount() > 0) {
                    System.out.println("Updated document " + docId + " with score " + score);
                } else {
                    System.out.println("No document found with ID: " + docId);
                }
            }
            System.out.println("Finished updating scores.");
        } catch (Exception e) {
            System.err.println("MongoDB error while updating scores: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public List<QueryDocument> populateScoresAndTotalword(List<QueryDocument> docs) {
        checkClientState();
        // Initialize resultList with the input docs to handle cases where a doc isn't found in DB
        List<QueryDocument> resultList = new ArrayList<>(docs); 

        System.out.printf("Got %d document to populate\n", docs.size());
        if (docs.isEmpty()) {
            return resultList; // Return immediately if input is empty
        }
        try {
            System.out.println("Populating documents for given URLs...");
            
            Map<String, Integer> urlToIndexMapping = new HashMap<>();
            for(int i = 0; i < docs.size(); i++) {
                urlToIndexMapping.put(docs.get(i).GetURL(), i);
            }
            
            List<String> urls = docs.stream().map(QueryDocument::GetURL).collect(Collectors.toList());
            FindIterable<Document> docsObject = documentsCollection.find(Filters.in("url", urls));

            for (Document docObj : docsObject) {
                String url = docObj.getString("url");
                Integer index = urlToIndexMapping.get(url); // Use Integer to handle potential null
                if (index != null) { // Check if the URL from DB matches one in the input list
                    QueryDocument doc = resultList.get(index); // Get the doc from resultList
                    // Safely get values, providing defaults if null
                    doc.SetPopularityScore(((Number) docObj.get("popularity_score")).doubleValue());
                    
                    doc.SetTotalWordCount(docObj.getInteger("totalWords") != null ? docObj.getInteger("totalWords") : 0);
                    doc.setTitle(docObj.getString("title") != null ? docObj.getString("title") : ""); // Set title
                    doc.setDescription(docObj.getString("description") != null ? docObj.getString("description") : ""); // Set description
                    // No need to put back into resultList as we modified the object in place
                } else {
                     System.out.println("Warning: URL from DB not found in input list: " + url);
                }
            }

            // Log how many documents were actually found and populated
            long populatedCount = resultList.stream().filter(d -> d.getTitle() != null).count(); // Example check
            System.out.println("Populated " + populatedCount + " out of " + docs.size() + " documents.");
            return resultList;
        } catch (Exception e) {
            System.err.println("MongoDB error while retrieving documents by URLs: " + e.getMessage());
            e.printStackTrace();
            // Return the list as is, possibly partially populated or empty on error
            return resultList; 
        }
    }


    public List<DocumentData> getAllDocuments() {
        checkClientState();
        List<DocumentData> documentsList = new ArrayList<>();
        try {
            System.out.println("Retrieving all documents from Documents collection...");
            for (Document doc : documentsCollection.find()) {
                DocumentData documentData = new DocumentData(
                    doc.getString("_id"),
                    doc.getString("url"),
                    doc.getString("title"),
                    doc.getString("description"),
                    doc.getString("content"),
                    doc.getList("links", String.class),
                    doc.getInteger("totalWords"),
                    doc.getDouble("popularity_score")
                );
                documentsList.add(documentData);
            }
            System.out.println("Retrieved " + documentsList.size() + " documents.");
            return documentsList;
        } catch (Exception e) {
            System.err.println("MongoDB error retrieving documents: " + e.getMessage());
            e.printStackTrace();
            return documentsList; // return empty list instead of null
        }
    }

    public void saveDocument(String docId, String url, String title, String description, String content, List<String> links, int totalWords) {
        checkClientState();
        try {
            Document doc = new Document("_id", docId)
                .append("url", url)
                .append("title", title)
                .append("description", description)
                .append("content", content)
                .append("links", links)
                .append("totalWords", totalWords)
            	.append("popularity_score",(Double)0.0);

            System.out.println("Saving document to Documents collection: " + docId);
            Bson filter = Filters.eq("_id", docId);
            documentsCollection.updateOne(filter, Updates.setOnInsert(doc), new UpdateOptions().upsert(true));
            System.out.println("Saved document to Documents collection: " + docId);
        } catch (Exception e) {
            System.err.println("MongoDB error saving document " + docId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void addOrUpdatePosting(String term, Posting posting) {
        checkClientState();
        try {
            Document fieldPositionsDoc = new Document();
            for (FieldType fieldType : posting.getFieldTypes()) {
                List<Integer> positions = posting.getPositions(fieldType);
                if (!positions.isEmpty()) {
                    fieldPositionsDoc.append(fieldType.name(), positions);
                }
            }

            Document postingDoc = new Document()
                .append("docId", posting.getDocId())
                .append("url", posting.getUrl())
                .append("fieldPositions", fieldPositionsDoc)
                .append("weight", posting.getWeight());

            System.out.println("Adding/updating posting for term: " + term + ", docId: " + posting.getDocId());
            Bson filter = Filters.eq("_id", term);

            Bson updateExisting = Updates.combine(
                Updates.set("postings.$[elem].fieldPositions", fieldPositionsDoc),
                Updates.set("postings.$[elem].url", posting.getUrl()),
                Updates.set("postings.$[elem].weight", posting.getWeight())
            );

            Document arrayFilter = new Document("elem.docId", posting.getDocId());

            UpdateOptions updateOptions = new UpdateOptions()
                .arrayFilters(List.of(arrayFilter));

            com.mongodb.client.result.UpdateResult result = collection.updateOne(
                Filters.and(filter, Filters.eq("postings.docId", posting.getDocId())),
                updateExisting,
                updateOptions
            );

            if (result.getMatchedCount() == 0) {
                Bson updateNew = Updates.push("postings", postingDoc);
                collection.updateOne(filter, updateNew, new UpdateOptions().upsert(true));
            }
            System.out.println("Added/updated posting for term: " + term);
        } catch (Exception e) {
            System.err.println("MongoDB error for term " + term + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void addOrUpdatePostings(List<Map.Entry<String, Posting>> updates) {
        checkClientState();
        try {
            List<WriteModel<Document>> bulkWrites = new ArrayList<>();
            for (Map.Entry<String, Posting> update : updates) {
                String term = update.getKey();
                Posting posting = update.getValue();
                Document fieldPositionsDoc = new Document();
                for (FieldType fieldType : posting.getFieldTypes()) {
                    List<Integer> positions = posting.getPositions(fieldType);
                    if (!positions.isEmpty()) {
                        fieldPositionsDoc.append(fieldType.name(), positions);
                    }
                }
                Document postingDoc = new Document()
                    .append("docId", posting.getDocId())
                    .append("url", posting.getUrl())
                    .append("fieldPositions", fieldPositionsDoc)
                    .append("weight", posting.getWeight());
                Bson filter = Filters.eq("_id", term);
                Bson updateExisting = Updates.combine(
                    Updates.set("postings.$[elem].fieldPositions", fieldPositionsDoc),
                    Updates.set("postings.$[elem].url", posting.getUrl()),
                    Updates.set("postings.$[elem].weight", posting.getWeight())
                );
                Document arrayFilter = new Document("elem.docId", posting.getDocId());
                bulkWrites.add(new UpdateOneModel<>(
                    Filters.and(filter, Filters.eq("postings.docId", posting.getDocId())),
                    updateExisting,
                    new UpdateOptions().arrayFilters(List.of(arrayFilter))
                ));
                bulkWrites.add(new UpdateOneModel<>(
                    filter,
                    Updates.push("postings", postingDoc),
                    new UpdateOptions().upsert(true)
                ));
            }
            if (!bulkWrites.isEmpty()) {
                System.out.println("Executing bulk write of " + bulkWrites.size() + " operations");
                collection.bulkWrite(bulkWrites);
                System.out.println("Completed bulk write");
            }
        } catch (Exception e) {
            System.err.println("MongoDB batch write error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public List<Posting> getPostings(String term) {
        checkClientState();
        try {
            List<Posting> postings = new ArrayList<>();
            Document termDoc = collection.find(Filters.eq("_id", term)).first();
            System.out.println("Raw document for term " + term + ": " + termDoc); // Debug
            if (termDoc != null) {
                List<Document> postingDocs = termDoc.getList("postings", Document.class);
                for (Document postingDoc : postingDocs) {
                    String docId = postingDoc.getString("docId");
                    String url = postingDoc.getString("url");
                    Document fieldPositionsDoc = postingDoc.get("fieldPositions", Document.class);

                    Posting posting = new Posting(docId, url);
                    for (Entry<String, Object> entry : fieldPositionsDoc.entrySet()) {
                        try {
                            FieldType fieldType = FieldType.valueOf(entry.getKey());
                            @SuppressWarnings("unchecked")
                            List<Integer> positions = (List<Integer>) entry.getValue();
                            for (Integer pos : positions) {
                                posting.addPosition(pos, fieldType);
                            }
                        } catch (IllegalArgumentException e) {
                            System.err.println("Invalid field type: " + entry.getKey());
                        }
                    }
                    postings.add(posting);
                }
            }
            System.out.println("Retrieved postings for term: " + term + ", count: " + postings.size());
            return postings;
        } catch (Exception e) {
            System.err.println("MongoDB read error for term " + term + ": " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    public Map<String, List<Posting>> getPostingsForTerms(List<String> terms) {
        checkClientState();
        Map<String, List<Posting>> results = new ConcurrentHashMap<>();
        int threadPoolSize = Math.min(terms.size(), Runtime.getRuntime().availableProcessors() * 2);
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);

        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (String term : terms) {
                futures.add(executor.submit(() -> {
                    try {
                        List<Posting> postings = getPostings(term);
                        if (!postings.isEmpty()) {
                            results.put(term, postings);
                        }
                    } catch (Exception e) {
                        System.err.println("Error retrieving postings for term " + term + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                    return null;
                }));
            }

            // Wait for all tasks to complete
            for (Future<Void> future : futures) {
                try {
                    future.get(10, TimeUnit.SECONDS); // Timeout after 10 seconds per task
                } catch (Exception e) {
                    System.err.println("Error in thread execution: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            System.out.println("Retrieved postings for " + results.size() + " terms.");
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        return results;
    }

    public Set<String> getTerms() {
        checkClientState();
        try {
            Set<String> terms = collection.distinct("_id", String.class).into(new java.util.HashSet<>());
            System.out.println("Retrieved terms count: " + terms.size());
            return terms;
        } catch (Exception e) {
            System.err.println("MongoDB error getting terms: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptySet();
        }
    }

    public int size() {
        checkClientState();
        try {
            int count = (int) collection.countDocuments();
            System.out.println("inverted_index size: " + count);
            return count;
        } catch (Exception e) {
            System.err.println("MongoDB error counting documents: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }

    public void close() {
        try {
            if (mongoClient != null) {
                mongoClient.close();
                System.out.println("MongoDB connection closed");
            }
        } catch (Exception e) {
            System.err.println("MongoDB close error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void checkClientState() {
        try {
            mongoClient.getDatabase("admin").runCommand(new Document("ping", 1));
        } catch (Exception e) {
            System.err.println("MongoDB client is not open or server is unreachable: " + e.getMessage());
            throw new IllegalStateException("MongoDB client is not in an open state", e);
        }
    }

    /**
     * Retrieves document details (URL, title, description) for a list of document IDs.
     *
     * @param docIds List of document IDs to fetch details for.
     * @return A Map where keys are docIds and values are Maps containing "url", "title", and "description".
     */
    public Map<String, Map<String, Object>> getDocumentsByIds(List<String> docIds) {
        Map<String, Map<String, Object>> results = new HashMap<>();
        if (docIds == null || docIds.isEmpty()) {
            return results;
        }

        MongoCollection<Document> collection = database.getCollection(DOCUMENTS_COLLECTION_NAME);
        Bson filter = Filters.in("_id", docIds); // Filter by document IDs

        try (MongoCursor<Document> cursor = collection.find(filter).iterator()) {
            while (cursor.hasNext()) {
                Document doc = cursor.next();
                String docId = doc.getString("_id");
                Map<String, Object> details = new HashMap<>();
                details.put("url", doc.getString("url"));
                details.put("title", doc.getString("title"));
                details.put("description", doc.getString("description"));
                // Add other fields if needed
                results.put(docId, details);
            }
        } catch (Exception e) {
            System.err.println("Error fetching documents by IDs from MongoDB: " + e.getMessage());
            // Handle exception appropriately, maybe log it
        }
        return results;
    }
}