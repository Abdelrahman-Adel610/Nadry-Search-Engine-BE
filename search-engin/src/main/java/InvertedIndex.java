

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InvertedIndex {
    private final Map<String, List<Posting>> index = new ConcurrentHashMap<>();
    
    // Simple field type enum for weighting
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
    
    /**
     * Adds a term to the index with field type information
     * 
     * @param term The term to add
     * @param docId The document ID
     * @param position The position in the document
     * @param fieldType The field where the term was found
     */
    public void addTerm(String term, String docId, int position, FieldType fieldType) {
        index.compute(term, (key, postings) -> {
            if (postings == null) {
                postings = new ArrayList<>();
            }
            
            // Check if the document already has this term
            boolean found = false;
            for (Posting posting : postings) {
                if (posting.getDocId().equals(docId)) {
                    posting.addPosition(position, fieldType);
                    found = true;
                    break;
                }
            }
            
            if (!found) {
                postings.add(new Posting(docId, position, fieldType));
            }
            
            return postings;
        });
    }
    
    /**
     * Legacy method for backward compatibility
     */
    public void addTerm(String term, String docId, int position) {
        addTerm(term, docId, position, FieldType.BODY);
    }
    
    public List<Posting> getPostings(String term) {
        return index.getOrDefault(term, Collections.emptyList());
    }
    
    public Set<String> getTerms() {
        return index.keySet();
    }
    
    public int size() {
        return index.size();
    }
    
    public static class Posting {
        private final String docId;
        private final Map<FieldType, List<Integer>> fieldPositions;
        private double weight = 0.0;
        
        public Posting(String docId, int position, FieldType fieldType) {
            this.docId = docId;
            this.fieldPositions = new EnumMap<>(FieldType.class);
            addPosition(position, fieldType);
        }
        
        /**
         * Add a position with field type information
         */
        public void addPosition(int position, FieldType fieldType) {
            fieldPositions.computeIfAbsent(fieldType, k -> new ArrayList<>()).add(position);
            // Update the weight based on field type
            weight += fieldType.getWeight();
        }
        
        public String getDocId() {
            return docId;
        }
        
        /**
         * Get all positions for all fields
         */
        public List<Integer> getPositions() {
            List<Integer> allPositions = new ArrayList<>();
            for (List<Integer> positions : fieldPositions.values()) {
                allPositions.addAll(positions);
            }
            return allPositions;
        }
        
        /**
         * Get positions for a specific field
         */
        public List<Integer> getPositions(FieldType fieldType) {
            return fieldPositions.getOrDefault(fieldType, Collections.emptyList());
        }
        
        /**
         * Get total frequency across all fields
         */
        public int getFrequency() {
            int count = 0;
            for (List<Integer> positions : fieldPositions.values()) {
                count += positions.size();
            }
            return count;
        }
        
        /**
         * Get frequency for a specific field
         */
        public int getFrequency(FieldType fieldType) {
            return fieldPositions.getOrDefault(fieldType, Collections.emptyList()).size();
        }
        
        /**
         * Get the combined weight of this posting
         */
        public double getWeight() {
            return weight;
        }
        
        /**
         * Get the field types where this term appears
         */
        public Set<FieldType> getFieldTypes() {
            return fieldPositions.keySet();
        }
    }
}

