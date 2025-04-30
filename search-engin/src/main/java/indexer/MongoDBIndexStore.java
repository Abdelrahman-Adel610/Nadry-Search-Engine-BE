package indexer;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.model.UpdateOneModel;
import org.bson.Document;
import org.bson.conversions.Bson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import indexer.InvertedIndex.FieldType;
import indexer.InvertedIndex.Posting;

public class MongoDBIndexStore {
    private final MongoClient mongoClient;
    private final MongoCollection<Document> collection;

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
            // Configure connection settings
            ConnectionString connString = new ConnectionString(connectionString + "?connectTimeoutMS=5000&socketTimeoutMS=5000&maxPoolSize=10");
            MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(connString)
                .retryWrites(true)
                .build();
            mongoClient = MongoClients.create(settings);
            MongoDatabase database = mongoClient.getDatabase(databaseName);
            collection = database.getCollection(collectionName);
            // Verify connection
            database.runCommand(new Document("ping", 1));
            System.out.println("Successfully connected to MongoDB database: " + databaseName + ", collection: " + collectionName);
        } catch (Exception e) {
            System.err.println("Failed to initialize MongoDB client: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("MongoDB initialization failed", e);
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
                .append("url", posting.getUrl()) // Added URL field
                .append("fieldPositions", fieldPositionsDoc)
                .append("weight", posting.getWeight());

            Bson filter = Filters.eq("_id", term);

            Bson updateExisting = Updates.combine(
                Updates.set("postings.$[elem].fieldPositions", fieldPositionsDoc),
                Updates.set("postings.$[elem].url", posting.getUrl()), // Added URL update
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
                    .append("url", posting.getUrl()) // Added URL field
                    .append("fieldPositions", fieldPositionsDoc)
                    .append("weight", posting.getWeight());
                Bson filter = Filters.eq("_id", term);
                Bson updateExisting = Updates.combine(
                    Updates.set("postings.$[elem].fieldPositions", fieldPositionsDoc),
                    Updates.set("postings.$[elem].url", posting.getUrl()), // Added URL update
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
                collection.bulkWrite(bulkWrites);
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

            if (termDoc != null) {
                List<Document> postingDocs = termDoc.getList("postings", Document.class);
                for (Document postingDoc : postingDocs) {
                    String docId = postingDoc.getString("docId");
                    String url = postingDoc.getString("url"); // Retrieve URL field
                    Document fieldPositionsDoc = postingDoc.get("fieldPositions", Document.class);

                    Posting posting = new Posting(docId, url); // Pass URL to constructor
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
            return postings;
        } catch (Exception e) {
            System.err.println("MongoDB read error for term " + term + ": " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    public Set<String> getTerms() {
        checkClientState();
        try {
            return collection.distinct("_id", String.class).into(new java.util.HashSet<>());
        } catch (Exception e) {
            System.err.println("MongoDB error getting terms: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptySet();
        }
    }

    public int size() {
        checkClientState();
        try {
            return (int) collection.countDocuments();
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
            // Attempt a lightweight operation to verify connection
            mongoClient.getDatabase("admin").runCommand(new Document("ping", 1));
        } catch (Exception e) {
            System.err.println("MongoDB client is not open or server is unreachable: " + e.getMessage());
            throw new IllegalStateException("MongoDB client is not in an open state", e);
        }
    }
}