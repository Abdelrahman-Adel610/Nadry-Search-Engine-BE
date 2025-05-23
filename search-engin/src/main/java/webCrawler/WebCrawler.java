package webCrawler;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Pattern;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.jsoup.select.Elements;

public class WebCrawler implements Runnable {
	private String start_url;
	//  REOMVED and using the database's data
	//	private static Set<String> compactStrings = new HashSet<String>();
	//	private static Set<String> visitedURLs = new HashSet<String>();
	//  private static Queue<String> linksQueue = new LinkedList<String>();
	private static final Pattern INVALID_FILENAME_CHARS = Pattern.compile("[\\\\/:*?\"<>|]");
	private final int MAX_PAGES_NUMBER = 6000;
	public static List<Path> paths = new LinkedList<>();
    public static List<String> links = new LinkedList<>();
	
	private static int crawledPages = 0;
	private static MongoJava database;
	private static RobotChecker robotChecker;
	private static LinkedList<String> seedLinks = new LinkedList<String>();
	private static boolean extractHyperLinks = true;	// to avoid making multiple reads from database after exceeding maxLimit
	
	public WebCrawler(LinkedList<String> links,int pageCount,MongoJava db) {
		this.crawledPages = pageCount;	// Current page count. If interrupted before, would be > 0
		this.database = db;
		this.robotChecker = new RobotChecker();
		for(String link:links) {
			database.enqueueUrl(link);
		}
	}
	
//	public void start() throws IOException {
//		database = new MongoJava("mongodb://localhost:27017/","Ndry");
//		robotChecker = new RobotChecker();
//		try {
//			crawl(start_url);
//		} catch (URISyntaxException e) {
//			e.printStackTrace();
//		}
//	}
	
	@Override
	public void run() {
		String url;
		String dir = "crawled_data";		
		while(crawledPages<MAX_PAGES_NUMBER) {
			// 1) getting link from Database 
			url = getLinkFromDB();	//a wrapper for dequeuing from DB to handle a case in threads
			if(url.contains("wikipedia"))
				continue;

			// 2) if visited once before
			if(database.isVisited(url)) {// <-- changed after database
				continue;
			}

			// 3) robots check
			if(!RobotChecker.isUrlAllowed(url)) {
				System.out.println("NOT ALLOWED");
				// marking it as visited to avoid re-checking on the eligibility of crawling
				database.markVisited(url);
				continue;
			}
			// 4) fetching the document
			Document doc = getDocument(url);
			if(doc == null) {
				continue;
			}
			
			// 5) getting its compact string
			String cs = calculateCompactString(doc);
			if(database.hasCompactString(cs)) {
				continue;
			}
			
			// adding the compact string to database
			// compactStrings.add(cs); <== removed after database
			database.addCompactString(cs);
			
//			// 6) downloading html doc inside a file
//			try {
//				// 6.1- creating fileName 
//				String filename;
//				filename = generateFilenameFromUrlPath(url);
//				// 6.2- path to store
//				Path storageDirectory = Paths.get("D:\\faculty stuff\\2nd year\\2nd term\\projects\\Ndry search engin\\search-engin\\", dir);
//				// checks if the given path exists or not, if not it creates one and so on.
//				Files.createDirectories(storageDirectory);
//				// creates an actual path
//				Path filePath = storageDirectory.resolve(filename+".html");
//				
//				String fullHTML = doc.outerHtml();
//				System.out.println(filePath);
//	
//				// adding the url and path to links and paths url for the indexer (mapping the results to user)
//				links.add(url);
//				paths.add(filePath);
//				
//				// 6.3- Write the HTML to the file (using UTF-8 encoding)
//			    Files.writeString(filePath, fullHTML, StandardCharsets.UTF_8);
//			} catch (URISyntaxException | IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
			
//			System.out.println(crawledPages+ " Thread "+Thread.currentThread().getId());
			System.out.println(crawledPages);
			
			// log current URL
//	        System.out.println("Crawling: " + url);
	        
	        // visitedURLs.add(url); <== removed after database
	        // 7) Mark it as visited
	        database.markVisited(url);
	        
	        // 8) inc the number of crawled Pages
//       	 	crawledPages++;
       	 	crawledPages = database.incrementAndGetCrawledCount();

					// 9) adding the html to the database to be indexed
					database.addCrawledPage(url,doc.html());

       	 	if(database.getQueueCount()<1000) {
       	 		extractHyperLinks = true;
       	 	}
       	 	if(!extractHyperLinks || database.getQueueCount()>= 2*MAX_PAGES_NUMBER) {
       	 		extractHyperLinks = false;
//	        	notifyAll();	// in case of a thread sleeping
       	 		continue;
       	 	}
       	 	
	        Elements links = doc.select("a[href]");
	        for(Element link:links) {
	        	 String nextUrl = link.absUrl("href");
	        	 if (nextUrl == null || nextUrl.isEmpty() ||
        			 !(nextUrl.toLowerCase().startsWith("http://") ||
        			  nextUrl.toLowerCase().startsWith("https://"))) {
    		        // skipping non-standard link
    		        System.out.println("   Skipping non-HTTP link: " + nextUrl);
    		        continue; // Skip to the next link
    		    }
	        	 // normalizing the link
	        	 String nextUrlNormalized="";
	        	 
				try {
					nextUrlNormalized = normalizeLink(nextUrl);
				} catch (URISyntaxException e) {
					e.printStackTrace();
				}
	             // check if nextUrl exists and link hasn't been visited 
	             if (!nextUrl.isEmpty() && !database.isVisited(nextUrlNormalized)) { 
	            	 synchronized (this) {
	                     database.enqueueUrl(nextUrlNormalized);
	                     notifyAll(); // Wake up waiting threads
	                 }
	             }
	        }
	        
		}
		System.out.println("finished crawling");
	}
	
	private String getLinkFromDB() {
		synchronized(this) {
			while(database.isQueueEmpty()) {
				try {
					System.out.println("Thread "+Thread.currentThread().getId()+ " is currently waiting for a link to crawler");
					wait();	// needs a notification to get back to work
					System.out.println("Thread "+Thread.currentThread().getId()+ " Woke up");
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			return database.dequeueUrl();			
		}
	}

	private static Document getDocument(String url) {
		try {
            // Getting connection response
			Connection.Response response = Jsoup.connect(url).userAgent("Ndry/1.0")
            		.timeout(3000)
            		.execute();
			// Checking if successful
			int status = response.statusCode();
			if(status!=200) {
				System.out.println("Skipped NON-OK response from url" + url);
				return null;
			}
			
			if(response.contentType()==null || !response.contentType().toLowerCase().contains("text/html")) {
				System.out.println("Skipped NON-HTML content from " + url);
				return null;
			}
			
			
            // return the HTML document
            return response.parse(); 
        } catch (IOException e) {
            // handle exceptions
            System.err.println("Unable to fetch HTML of: " + url);
        }
        return null;
    }
	
	private static String calculateCompactString(Document doc) {
		try {
			
			Elements elements = doc.body().select("*");	// selects all content
			
			String[] list = elements.text().split("\\s+");	// Splitting by One or More Whitespace Characters
			String cs = "";
			for(String word:list) {
				word = word.trim();
				if(word.length()>2 && Character.isLetterOrDigit(word.charAt(0))) // appending only char or digit
					cs+=word.charAt(0);
			}

			return cs;
			
		}catch(Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	private static String normalizeLink(String url) throws URISyntaxException {
		URL initUrl = null;
		try {
			// Use URI to parse and then convert to URL to avoid deprecated constructor
			URI uri = new URI(url);
			initUrl = uri.toURL();
		} catch (MalformedURLException | URISyntaxException e) { // Catch both exceptions
			System.err.println("Error parsing URL: " + url + " - " + e.getMessage());
			// Depending on desired behavior, you might want to return null or throw
			throw new URISyntaxException(url, "Malformed URL or URI syntax error"); // Re-throw as URISyntaxException for consistency
		}
		// extracting url part using URL for better parsing
		String scheme = initUrl.getProtocol();
	    String host = initUrl.getHost();
	    int port = initUrl.getPort(); // Returns -1 if not specified
	    String path = initUrl.getPath();
		    
	    URI normalizedUri = new URI(
	            scheme.toLowerCase(),  
	            null,                    
	            host.toLowerCase(),     
	            port,                  
	            path,                    
	            null,                    
	            null                     
	    );
	    String normalizedLink = normalizedUri.toString();
		return normalizedLink;
	}
	
	public static String generateFilenameFromUrlPath(String urlString) throws URISyntaxException {
		
        URI uri = new URI(urlString);
        String authority = uri.getHost();
        String path = uri.getPath();    

        // Handle root path
        if (path == null || path.isEmpty() || path.equals("/")) {
            path = "_root";
        } else {
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
        }
        // creating the filename a compo between authority & path
        String base = authority+"_"+path; // e.g., www.example.com_some_page.html
        
        // removing illegal chars
        String sanitizedName = INVALID_FILENAME_CHARS.matcher(base).replaceAll("_");

        return sanitizedName;
    }
	
	public static List<Path> getPaths(){
		return paths;
	}
	
	public static List<String> getLinks(){
		return links;
	}
	
	

}
