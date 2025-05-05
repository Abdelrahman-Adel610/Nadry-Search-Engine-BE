package nadry.indexer;

import java.util.List;

public class DocumentData {
    private final String docId;
    private final String url;
    private final String title;
    private final String description;
    private final String content;
    private final List<String> links;
    private final int totalWords;
    private final double popularityScore;

    public DocumentData(String docId, String url, String title, String description, String content, List<String> links, int totalWords, double popularityScore) {
        this.docId = docId;
        this.url = url;
        this.title = title;
        this.description = description;
        this.content = content;
        this.links = links;
        this.totalWords = totalWords;
        this.popularityScore = popularityScore;
    }

    public String getDocId() {
        return docId;
    }

    public String getUrl() {
        return url;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getContent() {
        return content;
    }

    public List<String> getLinks() {
        return links;
    }

    public int getTotalWords() {
        return totalWords;
    }

    @Override
    public String toString() {
        return "DocumentData{" +
               "docId='" + docId + '\'' +
               ", url='" + url + '\'' +
               ", title='" + title + '\'' +
               ", description='" + description + '\'' +
               ", content='" + content + '\'' +
               ", links=" + links +
               ", totalWords=" + totalWords +
               '}';
    }
}