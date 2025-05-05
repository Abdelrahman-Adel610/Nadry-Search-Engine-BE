package nadry.webCrawler;

import java.util.LinkedList;
import java.util.List;
import org.bson.Document;

import org.jsoup.Jsoup;
import java.io.IOException;

public class CrawlerWrapper {
  MongoJava database;
  List<String> links = new LinkedList<>();
  List<String> documents = new LinkedList<>();
  public CrawlerWrapper(MongoJava db) {
    this.database = db;
    List<Document> docs = database.getAllDocuments();
    String html;
    String url;
    for (Document doc : docs) {
      url = doc.getString("url");
      html = doc.getString("html");
      links.add(url);
      documents.add(html);
    }
  }

  public void main(String[] args) throws IOException {
    // This method is not used in this class, but it can be used to test the CrawlerWrapper class independently.
    
  }
  public List<String> getCrawledUrls() {
    return links;
  }

  public List<String> getCrawledHtml (){
    return documents;
  }
}
