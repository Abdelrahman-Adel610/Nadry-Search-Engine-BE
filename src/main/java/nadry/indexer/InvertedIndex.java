package nadry.indexer;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class InvertedIndex {
    private final Map<String, List<Posting>> index = new ConcurrentHashMap<>();
    private final MongoDBIndexStore mongoStore;
    private final BlockingQueue<Map.Entry<String, Posting>> updateQueue;
    private final ExecutorService consumerExecutor;
    private static final int BATCH_SIZE = 10000;
    private static final int QUEUE_CAPACITY = 1000000; // Adjust based on needs
    private static final int NUM_CONSUMER_THREADS = 32; // Configurable number of consumer threads
    private volatile boolean running = true;

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
            this.updateQueue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
            this.consumerExecutor = Executors.newFixedThreadPool(NUM_CONSUMER_THREADS);
            startBatchConsumers();
        } catch (Exception e) {
            System.err.println("Failed to initialize MongoDBIndexStore: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("InvertedIndex initialization failed", e);
        }
    }

    private void startBatchConsumers() {
        for (int i = 0; i < NUM_CONSUMER_THREADS; i++) {
            consumerExecutor.submit(() -> {
                List<Map.Entry<String, Posting>> batch = new ArrayList<>();
                while (running) {

                    try {
                        // Take the first update, blocking if queue is empty
                        Map.Entry<String, Posting> update = updateQueue.take();
                        batch.add(update);

                        // Drain additional updates up to BATCH_SIZE
                        updateQueue.drainTo(batch, BATCH_SIZE - 1);

                        if (!batch.isEmpty()) {
                            System.out.println("Thread " + Thread.currentThread().getName() + 
                                               " processing batch of " + batch.size() + " updates");
                            flushBatch(batch);
                            batch.clear();
                        }
                    } catch (InterruptedException e) {
                        System.err.println("Batch consumer interrupted in thread " + 
                                           Thread.currentThread().getName() + ": " + e.getMessage());
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        System.err.println("Error processing batch in thread " + 
                                           Thread.currentThread().getName() + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            });
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

        try {
            // Add to queue, will block if queue is full
            updateQueue.put(Map.entry(term, posting));
//            System.out.println("Enqueued update for term: " + term + ", docId: " + posting.getDocId());
        } catch (InterruptedException e) {
            System.err.println("Interrupted while enqueuing update for term: " + term);
            Thread.currentThread().interrupt();
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
        running = false;
        try {
            // Wait for remaining updates to process
            consumerExecutor.shutdown();
            if (!consumerExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                System.err.println("Batch consumer did not terminate within timeout");
                consumerExecutor.shutdownNow();
            }
            // Process any remaining updates
            List<Map.Entry<String, Posting>> remaining = new ArrayList<>();
            updateQueue.drainTo(remaining);
            if (!remaining.isEmpty()) {
                System.out.println("Flushing " + remaining.size() + " remaining updates");
                flushBatch(remaining);
            }
        } catch (InterruptedException e) {
            System.err.println("Interrupted during close: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
        mongoStore.close();
    }

    private void flushBatch(List<Map.Entry<String, Posting>> updates) {
        Map<String, Map<String, Posting>> termToPostings = new HashMap<>();
        for (Map.Entry<String, Posting> update : updates) {
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
    }

    public static class Posting {
        private final String docId;
        private final String url;
        private final Map<FieldType, List<Integer>> fieldPositions;
        private double weight = 0.0;
        private double popularity_score;

        public Posting(String docId, String url) {
            this.docId = docId;
            this.url = url;
            this.fieldPositions = new EnumMap<>(FieldType.class);
        }

        public void addPosition(int position, FieldType fieldType) {
            fieldPositions.computeIfAbsent(fieldType, k -> new ArrayList<>()).add(position);
            weight += fieldType.getWeight();
        }

        public String getDocId() {
            return docId;
        }

        public String getUrl() {
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
    }
}