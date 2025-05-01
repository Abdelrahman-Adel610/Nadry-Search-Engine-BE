package indexer;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.mongodb.client.MongoDatabase;

public class testMultiQuery {

	public static void main(String[] args) {
		
        // MongoDB configuration
        String mongoConnectionString = System.getenv("MONGO_URI") != null
            ? System.getenv("MONGO_URI")
            : "mongodb://localhost:27017/search_engine";
        String databaseName = "search_engine";
        String collectionName = "inverted_index";
        
		
        MongoDBIndexStore  mongoStore = new MongoDBIndexStore(mongoConnectionString, databaseName, collectionName);

         // Clear collections
         MongoDatabase database = mongoStore.getDatabase();
		
		
		 System.out.println("Multithreaded postings retrieval testing completed.");

         // Test with terms from the index
         System.out.println("Testing multithreaded postings retrieval with indexed terms...");
         Set<String> allTerms = mongoStore.getTerms();
         System.out.println("Total terms in index: " + allTerms.size());
         if (allTerms.isEmpty()) {
             System.out.println("WARNING: No terms found in inverted_index collection!");
         } else {
             List<String> sampleTerms = allTerms.stream().limit(3).collect(Collectors.toList());
             System.out.println("Querying sample terms from index: " + sampleTerms);
             Map<String, List<InvertedIndex.Posting>> postingsMap = mongoStore.getPostingsForTerms(sampleTerms);
             System.out.println("Results for sample terms query:");
             for (Map.Entry<String, List<InvertedIndex.Posting>> entry : postingsMap.entrySet()) {
                 String term = entry.getKey();
                 List<InvertedIndex.Posting> postings = entry.getValue();
                 System.out.println("Term: " + term);
                 if (postings.isEmpty()) {
                     System.out.println("  No postings found.");
                 } else {
                     for (InvertedIndex.Posting posting : postings) {
                         System.out.println("  DocID: " + posting.getDocId() + ", URL: " + posting.getUrl() +
                                            ", Weight: " + posting.getWeight());
                         for (InvertedIndex.FieldType fieldType : posting.getFieldTypes()) {
                             System.out.println("    Field: " + fieldType + ", Positions: " + posting.getPositions(fieldType));
                         }
                     }
                 }
             }
         }
         System.out.println("Indexed terms retrieval testing completed.");


	}

}
