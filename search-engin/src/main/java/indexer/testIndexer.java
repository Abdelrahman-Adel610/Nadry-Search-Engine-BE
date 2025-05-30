//package indexer;
//
//
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.util.Arrays;
//import java.util.List;

//public class testIndexer {
//    public static void main(String[] args) throws Exception {
//        try {
//            // Define document path and URL
//            Path filePath = Paths.get("/home/salah/Public/project/Nadry/Nadry-Search-Engine-BE/search-engin/src/main/java/test.html");
//            String url = "https://example.com/test.html";
//
//            if (!Files.exists(filePath)) {
//                throw new DocumentProcessingException("File does not exist: " + filePath, null);
//            }
//
//            // Initialize components
//            DocumentProcessor processor = new DocumentProcessor();
//            StopWordFilter stopWordFilter = new StopWordFilter();
//            Tokenizer tokenizer = new Tokenizer(stopWordFilter);
//            IndexBuilder indexBuilder = new IndexBuilder(processor, tokenizer);
//
//            // Build index
//            List<Path> documentPaths = Arrays.asList(filePath);
//            List<String> urls = Arrays.asList(url);
//            indexBuilder.buildIndex(documentPaths, urls);
//
//            // Query and verify index
//            InvertedIndex index = getIndex(indexBuilder);
//            System.out.println("\nVerifying InvertedIndex Contents:");
//            System.out.println("Total unique terms: " + index.size());
//
//            // Print all terms and their postings
//            for (String term : index.getTerms()) {
//                List<InvertedIndex.Posting> postings = index.getPostings(term);
//                System.out.println("Term: " + term);
//                for (InvertedIndex.Posting posting : postings) {
//                    System.out.println("  DocID: " + posting.getDocId());
//                    System.out.println("  Frequency: " + posting.getFrequency());
//                    System.out.println("  Weight: " + posting.getWeight());
//                    System.out.println("  Field Types: " + posting.getFieldTypes());
//                    System.out.println("  Positions (TITLE): " + posting.getPositions(InvertedIndex.FieldType.TITLE));
//                    System.out.println("  Positions (DESCRIPTION): " + posting.getPositions(InvertedIndex.FieldType.DESCRIPTION));
//                    System.out.println("  Positions (BODY): " + posting.getPositions(InvertedIndex.FieldType.BODY));
//                }
//            }
//
//            // Verify specific tokens
//            System.out.println("\nVerifying Specific Tokens:");
//            String[] testTerms = {"file", "email:test@example.com", "various"};
//            for (String term : testTerms) {
//                List<InvertedIndex.Posting> postings = index.getPostings(term);
//                if (postings.isEmpty()) {
//                    System.out.println("ERROR: Term '" + term + "' not found in index");
//                } else {
//                    System.out.println("Term '" + term + "' found with " + postings.size() + " postings");
//                    for (InvertedIndex.Posting posting : postings) {
//                        System.out.println("  DocID: " + posting.getDocId() + ", Frequency: " + posting.getFrequency());
//                    }
//                }
//            }
//
//        } catch (DocumentProcessingException e) {
//            System.err.println("Error during indexing: " + e.getMessage());
//            e.printStackTrace();
//        }
//    }
//
//    // Helper method to access private index field (since InvertedIndex is not modifiable)
//    private static InvertedIndex getIndex(IndexBuilder builder) throws Exception {
//        java.lang.reflect.Field indexField = IndexBuilder.class.getDeclaredField("index");
//        indexField.setAccessible(true);
//        return (InvertedIndex) indexField.get(builder);
//    }
//}