package nadry.api;



public class SearchResult {
    private final String docId;
    private final String url;
    private final String title;
    private final String description;
    private final double score;

    public SearchResult(String docId, String url, String title, String description, double score) {
        this.docId = docId;
        this.url = url;
        this.title = title;
        this.description = description;
        this.score = score;
    }

    // Getters
    public String getDocId() { return docId; }
    public String getUrl() { return url; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public double getScore() { return score; }
}