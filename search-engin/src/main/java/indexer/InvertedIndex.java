package indexer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InvertedIndex {
    private final Map<String, List<Posting>> index = new ConcurrentHashMap<>();
    private final MongoDBIndexStore mongoStore;
    private final List<Map.Entry<String, Posting>> pendingUpdates = Collections.synchronizedList(new ArrayList<>());
    private static final int BATCH_SIZE = 1000;

    public enum FieldType {
        TITLE(3.0),
        DESCRIPTION(1.5),
        BODY(1.0);

        private final double weight;

        FieldType(double weight) {
            this.weight = weight;
        }

        public double getWeight() {
            return weight;
        }
    }

    public InvertedIndex(String mongoConnectionString, String databaseName, String collectionName) {
        try {
            this.mongoStore = new MongoDBIndexStore(mongoConnectionString, databaseName, collectionName);
        } catch (Exception e) {
            System.err.println("Failed to initialize MongoDBIndexStore: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("InvertedIndex initialization failed", e);
        }
    }

    public void addTerm(String term, Posting posting) {
        if (mongoStore == null) {
            System.err.println("MongoDBIndexStore is null, cannot add term: " + term);
            return;
        }
        if (term == null || term.isEmpty() || posting == null) {
            System.err.println("Invalid term or posting: term=" + term + ", posting=" + posting);
            return;
        }

        synchronized (index) {
            List<Posting> postings = index.computeIfAbsent(term, k -> Collections.synchronizedList(new ArrayList<>()));
            synchronized (postings) {
                boolean found = false;
                for (Posting existingPosting : postings) {
                    if (existingPosting.getDocId().equals(posting.getDocId())) {
                        for (FieldType fieldType : posting.getFieldTypes()) {
                            List<Integer> positions = posting.getPositions(fieldType);
                            for (Integer pos : positions) {
                                existingPosting.addPosition(pos, fieldType);
                            }
                        }
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    postings.add(posting);
                }
            }
        }

        synchronized (pendingUpdates) {
            boolean merged = false;
            for (Map.Entry<String, Posting> entry : pendingUpdates) {
                if (entry.getKey().equals(term) && entry.getValue().getDocId().equals(posting.getDocId())) {
                    for (FieldType fieldType : posting.getFieldTypes()) {
                        List<Integer> positions = posting.getPositions(fieldType);
                        for (Integer pos : positions) {
                            entry.getValue().addPosition(pos, fieldType);
                        }
                    }
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                pendingUpdates.add(Map.entry(term, posting));
            }
            if (pendingUpdates.size() >= BATCH_SIZE) {
                System.out.println("Writing batch of " + pendingUpdates.size() + " updates to MongoDB");
                flushPendingUpdates();
            }
        }
    }

    public List<Posting> getPostings(String term) {
        if (mongoStore == null) {
            System.err.println("MongoDBIndexStore is null, cannot get postings for term: " + term);
            return Collections.emptyList();
        }
        // Always retrieve from MongoDB, bypassing in-memory cache
        List<Posting> postings = mongoStore.getPostings(term);
        if (!postings.isEmpty()) {
            // Update in-memory cache for consistency
            index.put(term, Collections.synchronizedList(new ArrayList<>(postings)));
        }
        return postings != null ? postings : Collections.emptyList();
    }

    public Set<String> getTerms() {
        if (mongoStore == null) {
            System.err.println("MongoDBIndexStore is null, cannot get terms");
            return Collections.emptySet();
        }
        Set<String> terms = new HashSet<>(index.keySet());
        terms.addAll(mongoStore.getTerms());
        return terms;
    }

    public int size() {
        if (mongoStore == null) {
            System.err.println("MongoDBIndexStore is null, cannot get size");
            return 0;
        }
        return mongoStore.size();
    }

    public void close() {
        if (mongoStore == null) {
            System.err.println("MongoDBIndexStore is null, cannot close");
            return;
        }
        synchronized (pendingUpdates) {
            if (!pendingUpdates.isEmpty()) {
                System.out.println("Flushing updates to MongoDB");
                flushPendingUpdates();
            }
        }
        mongoStore.close();
    }

    public void flushPendingUpdates() {
        Map<String, Map<String, Posting>> termToPostings = new HashMap<>();
        for (Map.Entry<String, Posting> update : pendingUpdates) {
            String term = update.getKey();
            Posting posting = update.getValue();
            termToPostings.computeIfAbsent(term, k -> new HashMap<>())
                .compute(posting.getDocId(), (docId, existing) -> {
                    if (existing == null) {
                        return posting;
                    }
                    for (FieldType fieldType : posting.getFieldTypes()) {
                        List<Integer> positions = posting.getPositions(fieldType);
                        for (Integer pos : positions) {
                            existing.addPosition(pos, fieldType);
                        }
                    }
                    return existing;
                });
        }

        List<Map.Entry<String, Posting>> deduplicatedUpdates = new ArrayList<>();
        for (Map.Entry<String, Map<String, Posting>> termEntry : termToPostings.entrySet()) {
            String term = termEntry.getKey();
            for (Posting posting : termEntry.getValue().values()) {
                deduplicatedUpdates.add(Map.entry(term, posting));
            }
        }

        if (!deduplicatedUpdates.isEmpty()) {
            mongoStore.addOrUpdatePostings(deduplicatedUpdates);
        }
        pendingUpdates.clear();
    }

    public static class Posting {
        private final String docId;
        private final String url; // Added URL field
        private final Map<FieldType, List<Integer>> fieldPositions;
        private double weight = 0.0;
        private double popularity_score;

        public Posting(String docId, String url) {
            this.docId = docId;
            this.url = url; // Initialize URL
            this.fieldPositions = new EnumMap<>(FieldType.class);
        }

        public void addPosition(int position, FieldType fieldType) {
            fieldPositions.computeIfAbsent(fieldType, k -> new ArrayList<>()).add(position);
            weight += fieldType.getWeight();
        }

        public String getDocId() {
            return docId;
        }

        public String getUrl() { // Added getter for URL
            return url;
        }

        public List<Integer> getPositions() {
            List<Integer> allPositions = new ArrayList<>();
            for (List<Integer> positions : fieldPositions.values()) {
                allPositions.addAll(positions);
            }
            return allPositions;
        }

        public List<Integer> getPositions(FieldType fieldType) {
            return fieldPositions.getOrDefault(fieldType, Collections.emptyList());
        }

        public int getFrequency() {
            int count = 0;
            for (List<Integer> positions : fieldPositions.values()) {
                count += positions.size();
            }
            return count;
        }

        public int getFrequency(FieldType fieldType) {
            return fieldPositions.getOrDefault(fieldType, Collections.emptyList()).size();
        }

        public double getWeight() {
            return weight;
        }

        public Set<FieldType> getFieldTypes() {
            return fieldPositions.keySet();
        }
        
        public double GetPopularityScore() {
        	return popularity_score;
        }
    
        public void SetPopularityScore(double score) {
        	popularity_score = score;
        }

        @Override
        public String toString() {
            // Create a string representation of the fieldPositions map
            StringBuilder positionsStr = new StringBuilder("{");
            fieldPositions.forEach((field, positions) -> {
                positionsStr.append(field.name())
                            .append("=")
                            .append(positions.toString())
                            .append(", ");
            });
            // Remove trailing comma and space if map is not empty
            if (positionsStr.length() > 1) {
                positionsStr.setLength(positionsStr.length() - 2);
            }
            positionsStr.append("}");

            return "Posting{" +
                   "docId='" + docId + '\'' +
                   ", url='" + url + '\'' +
                   ", weight=" + weight +
                   ", popularity_score=" + popularity_score +
                   ", fieldPositions=" + positionsStr.toString() +
                   '}';
        }
    }
}