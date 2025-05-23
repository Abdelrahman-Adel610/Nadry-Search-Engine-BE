package webCrawler;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

public class WebCrawlerMain {
	private final static int MAX_PAGES_NUMBER = 6000;
	public static void main(String []args) throws IOException {
//		String url = "https://www.york.ac.uk/teaching/cws/wws/webpage1.html";
		String url = "https://en.wikipedia.org/wiki/Wiki";
		LinkedList<String> seedLinks = new LinkedList<String>();
		MongoJava database = new MongoJava("mongodb://localhost:27017/","Ndry");
		// CrawlerWrapper crawler = new CrawlerWrapper(database);
		// List<String> docs  = crawler.getCrawledDocuments();
    // for(String doc : docs) {
			//   System.out.println(doc);
			//   System.out.println("--------------------------------------------------");
			// }
			// System.out.println("Number of crawled documents: " + docs.size());
			int pageCount = database.getCrawledCount();
		if(pageCount>=MAX_PAGES_NUMBER || pageCount==0) {
			// means that it either finished crawling once before or it didn't start at all
			pageCount = 0;
//			seedLinks.add("https://en.wikipedia.org/wiki/Main_Page");
			seedLinks.add("https://www.reddit.com/r/technology/");
			seedLinks.add("https://news.google.com/");
			seedLinks.add("https://www.theverge.com/tech");
//			seedLinks.add("https://asia.nikkei.com/");
//			seedLinks.add("https://technicalseo.com/");
//			seedLinks.add("https://www.facebook.com/");
//			seedLinks.add("https://news.ycombinator.com/");
			
		}	//else: means it got interrupted and got back again

		
		WebCrawler crawler = new WebCrawler(seedLinks,pageCount,database);
		Scanner sc=new Scanner(System.in);
        System.out.println("Please Enter the number of threads you want: ");
        int num=sc.nextInt();
        Thread threads[] = new Thread[num];
    
        for (int i = 0; i < num; i++) {
            threads[i] = new Thread(crawler);
            threads[i].start();
        }
        for (int i = 0; i < num; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {
                System.out.println("Thread "+i+" is interrupted");
                throw new RuntimeException(e);
            }
        }
	}
}
