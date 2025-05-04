package nadry.ranker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


public class RankerApplication {
    public static void main(String[] args) {
    	String query = "item order arrived";
    	
    	//This map contains frequencies for each term in the query
    	Map<String, Integer> queryTermFrequencsy= new HashMap<String, Integer>();
    	queryTermFrequencsy.put("item", 1);
    	queryTermFrequencsy.put("order", 1);
    	queryTermFrequencsy.put("arrive", 1);
    	/////////////
   
    	ArrayList<QueryDocument> docs = new ArrayList<QueryDocument>();
    	
    	///////////////////////// This is added for each matching document
    	
    	//This map contains frequencies for each term in the doc
    	Map<String, Integer> doc1TermFrequencsy= new HashMap<String, Integer>();
    	doc1TermFrequencsy.put("item", 12);
    	doc1TermFrequencsy.put("order", 12);

//    	docs.add(new QueryDocument(
//    			"https://example.com/sample2.html",
//    			doc1TermFrequencsy));
    	//////////////////////
    	
    	String connectionString = "dummy connection string";
    	Ranker r = new Ranker(connectionString);
    	r.Rank(queryTermFrequencsy, docs);
    	
    }
}
