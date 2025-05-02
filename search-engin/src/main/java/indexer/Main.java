package indexer;

import com.mongodb.client.MongoDatabase;
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
        ExecutorService executor = Executors.newFixedThreadPool(4);
        InvertedIndex index = null;
        MongoDBIndexStore mongoStore = null;

        try {
            // Define HTML strings and URLs
            String url1 = "https://example.com/test.html";
            String url2 = "https://example.com/sample1.html";
            String url3 = "https://example.com/sample2.html";
            String html1 = "<!DOCTYPE html>\n" +
                           "<html lang=\"en\">\n" +
                           "<head>\n" +
                           "  <meta charset=\"UTF-8\">\n" +
                           "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                           "  <title>Sample HTML Page</title>\n" +
                           "</head>\n" +
                           "<body>\n" +
                           "  <h1>Hello, World!</h1>\n" +
                           "  <p>This is a sample HTML mine page salah MAMA .</p>\n" +
                           "  <a href=\"https://www.example.com\">Visit Example</a>\n" +
                           "</body>\n" +
                           "</html>\n";
            String html2 = "<!DOCTYPE html>\n" +
                           "<html lang=\"en\">\n" +
                           "<head>\n" +
                           "  <meta charset=\"UTF-8\">\n" +
                           "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                           "  <title>Sample HTML Page</title>\n" +
                           "</head>\n" +
                           "<body>\n" +
                           "  <h1>Hello, World! MAMA</h1>\n" +
                           "  <p>This is a sample HTML aref page.</p>\n" +
                           "  <a href=\"https://www.example.com\">Visit Example</a>\n" +
                           "</body>\n" +
                           "</html>\n";
            
            String html3="<!DOCTYPE html>\n" + //
                                "<html lang=\"en\">\n" + //
                                "<head>\n" + //
                                "    <meta charset=\"UTF-8\">\n" + //
                                "    <title>Sample Index Page</title>\n" + //
                                "    <meta name=\"description\" content=\"This is a large HTML sample for testing indexing logic.\">\n" + //
                                "    <style>\n" + //
                                "        body { font-family: Arial, sans-serif; }\n" + //
                                "        .important { color: red; }\n" + //
                                "    </style>\n" + //
                                "    <script>\n" + //
                                "        console.log(\"Sample script running...\");\n" + //
                                "        function test() {\n" + //
                                "            alert(\"Test function\");\n" + //
                                "        }\n" + //
                                "    </script>\n" + //
                                "</head>\n" + //
                                "<body>\n" + //
                                "    <header>\n" + //
                                "        <h1>Welcome to the Sample Indexing Page</h1>\n" + //
                                "        <nav>\n" + //
                                "            <ul>\n" + //
                                "                <li><a href=\"#section1\">Section 1</a></li>\n" + //
                                "                <li><a href=\"#section2\">Section 2</a></li>\n" + //
                                "                <li><a href=\"#section3\">Section 3</a></li>\n" + //
                                "            </ul>\n" + //
                                "        </nav>\n" + //
                                "    </header>\n" + //
                                "\n" + //
                                "    <main>\n" + //
                                "        <section id=\"section1\">\n" + //
                                "            <h2>Section 1: Introduction</h2>\n" + //
                                "            <p>This section introduces the indexing test. It contains various elements to simulate a realistic page.</p>\n" + //
                                "            <p class=\"important\">Important content appears here for indexing engines to pick up.</p>\n" + //
                                "        </section>\n" + //
                                "\n" + //
                                "        <section id=\"section2\">\n" + //
                                "            <h2>Section 2: Data</h2>\n" + //
                                "            <table border=\"1\">\n" + //
                                "                <tr>\n" + //
                                "                    <th>Name</th><th>Value</th><th>Description</th>\n" + //
                                "                </tr>\n" + //
                                "                <tr>\n" + //
                                "                    <td>Alpha</td><td>100</td><td>First test entry.</td>\n" + //
                                "                </tr>\n" + //
                                "                <tr>\n" + //
                                "                    <td>Beta</td><td>200</td><td>Second test entry.</td>\n" + //
                                "                </tr>\n" + //
                                "                <tr>\n" + //
                                "                    <td>Gamma</td><td>300</td><td>Third test entry with some <strong>bold text</strong>.</td>\n" + //
                                "                </tr>\n" + //
                                "            </table>\n" + //
                                "        </section>\n" + //
                                "\n" + //
                                "        <section id=\"section3\">\n" + //
                                "            <h2>Section 3: Content Blocks</h2>\n" + //
                                "            <div>\n" + //
                                "                <p>Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vestibulum vehicula erat at sapien tempus, ac suscipit velit sagittis.</p>\n" + //
                                "                <p>Curabitur vel eros at orci laoreet tincidunt nec ut turpis. Aenean ac libero nec justo fermentum imperdiet.</p>\n" + //
                                "                <p>Aliquam erat volutpat. Proin quis dictum neque. Nulla facilisi. Integer condimentum libero a leo congue volutpat.</p>\n" + //
                                "            </div>\n" + //
                                "            <div>\n" + //
                                "                <img src=\"sample.jpg\" alt=\"Sample Image\" width=\"300\">\n" + //
                                "                <p>This image has an alt text MAMA to be considered in indexing.</p>\n" + //
                                "            </div>\n" + //
                                "        </section>\n" + //
                                "\n" + //
                                "        <section id=\"section4\">\n" + //
                                "            <h2>Section 4: Lists and Links</h2>\n" + //
                                "            <ul>\n" + //
                                "                <li>First item</li>\n" + //
                                "                <li>Second item</li>\n" + //
                                "                <li>Third item</li>\n" + //
                                "            </ul>\n" + //
                                "            <ol>\n" + //
                                "                <li>Ordered one</li>\n" + //
                                "                <li>Ordered two</li>\n" + //
                                "                <li>Ordered three</li>\n" + //
                                "            </ol>\n" + //
                                "            <p>Visit <a href=\"https://example.com\">this external link</a> or check our <a href=\"/internal.html\">internal resources</a>.</p>\n" + //
                                "        </section>\n" + //
                                "\n" + //
                                "        <section id=\"section5\">\n" + //
                                "            <h2>Section 5: More Text</h2>\n" + //
                                "            <p>\n" + //
                                "                Here's a long block of salah text for more indexing:\n" + //
                                "                The quick brown fox jumps over the lazy dog. The rain in Spain stays mainly in the plain.\n" + //
                                "                She sells seashells by the seashore. How much wood would a woodchuck chuck if a woodchuck could chuck wood?\n" + //
                                "                Peter Piper picked a peck of pickled peppers. A peck of pickled peppers Peter Piper picked.\n" + //
                                "            </p>\n" + //
                                "        </section>\n" + //
                                "    </main>\n" + //
                                "\n" + //
                                "    <footer>\n" + //
                                "        <p>© 2025 Sample Corp. All rights reserved.</p>\n" + //
                                "        <p>Contact: <a href=\"mailto:info@example.com\">info@example.com</a></p>\n" + //
                                "    </footer>\n" + //
                                "</body>\n" + //
                                "</html>\n" + //
                                "";

            List<String> htmls = new LinkedList<>();
            List<String> urls = new LinkedList<>();
            htmls.add(html1);
            htmls.add(html2);
            htmls.add(html3);
            urls.add(url1);
            urls.add(url2);
            urls.add(url3);

            // Compute docIds for debugging and testing (matching DocumentProcessor's generateDocId)
            String[] docIds = new String[3];
            docIds[0] = computeSha256(url1);
            docIds[1] = computeSha256(url2);
            docIds[1] = computeSha256(url3);

            // MongoDB configuration
            String mongoConnectionString = System.getenv("MONGO_URI") != null
                ? System.getenv("MONGO_URI")
                : "mongodb://localhost:27017/search_engine";
            String databaseName = "search_engine";
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

            // Print input
            System.out.println("URLs:");
            for (String url : urls) {
                System.out.println(url);
            }
            System.out.println("--------------------------------------");
            System.out.println("Document HTMLs and DocIDs:");
            for (int i = 0; i < htmls.size(); i++) {
                System.out.println(htmls.get(i) + " -> " + docIds[i]);
            }
            System.out.println("--------------------------------------");

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

            // Test document retrieval
            System.out.println("Testing document retrieval...");
            for (String docId : docIds) {
                DocumentData doc = mongoStore.getDocument(docId);
                if (doc == null) {
                    System.out.println("ERROR: Document '" + docId + "' not found in Documents collection");
                } else {
                    System.out.println("Document '" + docId + "' found:");
                    System.out.println("  URL: " + doc.getUrl());
                    System.out.println("  Title: " + doc.getTitle());
                    System.out.println("  Description: " + doc.getDescription());
                    System.out.println("  Content: " + doc.getContent());
                    System.out.println("  Links: " + doc.getLinks());
                    System.out.println("  Total Words: " + doc.getTotalWords());
                }
            }
            System.out.println("Document retrieval testing completed.");

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