package nadry.webCrawler;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Scanner;

public class WebCrawlerMain {
	private final static int MAX_PAGES_NUMBER = 6000;
	public static void main(String []args) throws IOException {
		LinkedList<String> seedLinks = new LinkedList<String>();
		MongoJava database = new MongoJava(nadry.Config.DATABASE_URL,"Ndry3");
		int pageCount = database.getCrawledCount();
		if(pageCount>=MAX_PAGES_NUMBER || pageCount==0) {
			// means that it either finished crawling once before or it didn't start at all
			pageCount = 0;
			seedLinks.add("https://www.reddit.com/r/technology/");
			seedLinks.add("https://www.bbc.com/news");
			seedLinks.add("https://medium.com/");

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
