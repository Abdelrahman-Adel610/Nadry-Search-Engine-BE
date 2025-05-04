package nadry.ranker;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import indexer.InvertedIndex.Posting;
import indexer.MongoDBIndexStore;

public class Ranker {

	MongoDBIndexStore db;
	public Ranker(String connection_string) {
		db = new MongoDBIndexStore(connection_string, "search_engine2", "inverted_index");
	}
	
	/*
	 * query Bag is a map that contains frequencies of query phrase
	 * pages Bag is a list of map that contains frequencies of query phrase
	 * @returns sorted pages
	 */
	public List<QueryDocument> Rank(Map<String, Integer> queryBag, List<QueryDocument> pagesBag) {
		pagesBag = db.populateScoresAndTotalword(pagesBag);
		
		for(QueryDocument d : pagesBag) {
			System.out.print(d.GetPopularityScore() + ", ");
		}
//		return pagesBag;
		NormlizePopularityScore(pagesBag);
		ArrayList<Double> relevanceScores = CalculateRelevenceScore(queryBag, pagesBag);
		System.out.printf("Calculated Relevence scores for %d documents\n", pagesBag.size());
		
		List<Map.Entry<Double, QueryDocument>> scoredDocs = new ArrayList<>();

	    for (int i = 0; i < pagesBag.size(); i++) {
	    	double relevance = relevanceScores.get(i);
	    	double popularity = pagesBag.get(i).GetPopularityScore();
	    	
	        double totalScore = relevance * 0.7 + popularity * 0.3;
	        
	        pagesBag.get(i).SetRelevenceScore(relevance);
	        pagesBag.get(i).setScore(totalScore);
	        
	        scoredDocs.add(new AbstractMap.SimpleEntry<>(totalScore, pagesBag.get(i)));
	    }

	    // Sort by totalScore descending
	    scoredDocs.sort((a, b) -> Double.compare(b.getKey(), a.getKey()));

	    // Extract sorted documents
	    ArrayList<QueryDocument> sortedDocs = new ArrayList<>();
	    for (Map.Entry<Double, QueryDocument> entry : scoredDocs) {
	        sortedDocs.add(entry.getValue());
	    }
	    
	    for(QueryDocument doc: sortedDocs) {
	    	System.out.printf("Url: %s, \nTitle: %s\n, Description: %s\n, Score: %f.\n\n", doc.GetURL(), doc.getTitle(), doc.getDescription(), doc.getScore());
	    }
	    
	    System.out.printf("Ranked %d results\n", sortedDocs.size());
	    return sortedDocs;
	}
	
	public void NormlizePopularityScore(List<QueryDocument> pagesBag) {
        double maxScore = 0;
        for(QueryDocument doc : pagesBag) maxScore = Math.max(maxScore, doc.GetPopularityScore());
        for(QueryDocument doc : pagesBag) doc.popularityScore /= maxScore;
	}
		
	/*
	 * query Bag is a map that contains frequencies of query phrase
	 * pages Bag is a list of map that contains frequencies of query phrase
	 */
	public ArrayList<Double> CalculateRelevenceScore(Map<String, Integer> queryBag, List<QueryDocument> pagesBag) {
	    int N = pagesBag.size(); // total documents

	    int queryLength = 0;
	    for (int freq : queryBag.values()) queryLength += freq;
	    
	    // Compute document frequency DF for each term
	    Map<String, Integer> docFreq = new HashMap<>();
	    for (QueryDocument queryDoc : pagesBag) {
	    	Map<String, Integer> doc = queryDoc.GetTermFrequency();
	        for (String term : doc.keySet()) {
	            docFreq.put(term, docFreq.getOrDefault(term, 0) + 1);
	        }
	    }

	    // Build query TF-IDF vector
	    Map<String, Double> queryTFIDF = calculateTFIDF(queryBag, queryLength, docFreq, N);
	    
	    for(QueryDocument d : pagesBag) d.QUERY_TFIDF = queryTFIDF;

	    // Normalize query vector
	    double queryNorm = normVector(queryTFIDF);

	    ArrayList<Double> scores = new ArrayList<Double>();
	    double maxScore = 0;
	    // Compare each document with the query
	    for (int i = 0; i < pagesBag.size(); i++) {
	        Map<String, Integer> doc = pagesBag.get(i).GetTermFrequency();

	        // Document TF-IDF
	        Map<String, Double> docTFIDF = calculateTFIDF(doc, pagesBag.get(i).GetTotalWord(), docFreq, N);
	        pagesBag.get(i).DOC_TFIDF = docTFIDF;

	        double relevanceScore = dotProduct(queryTFIDF, queryNorm, docTFIDF);
	        maxScore = Math.max(maxScore, relevanceScore);
//	        System.out.printf("Document %d Relevence: %.4f\n", i + 1, relevanceScore);
	        scores.add(relevanceScore);
	    }
	    for(int i = 0; i < scores.size(); i++) scores.set(i, scores.get(i)/ maxScore);
	    return scores;
	}
	
	/*
	 * Calculated TF-IDF vector for a document
	 */
	public Map<String, Double> calculateTFIDF(Map<String, Integer> doc, int docLength, Map<String, Integer> docFreq, int TotalDocs) {
//		int docLength = 0;
//        for (int freq : doc.values()) docLength += freq;

        Map<String, Double> docTFIDF = new HashMap<>();
        for (Map.Entry<String, Integer> entry : doc.entrySet()) {
            String term = entry.getKey();
            int tf = entry.getValue();
            int df = docFreq.getOrDefault(term, 0);
            double idf = Math.log10((double) TotalDocs / (1 + df));
            double tfidf = (tf / (double) docLength) * idf;
            docTFIDF.put(term, tfidf);
        }
        
        return docTFIDF;
	}
	
	/*
	 * dot product between two TFIDF vectors
	 */
	public double dotProduct(Map<String, Double> queryTFIDF, double queryNorm, Map<String, Double> docTFIDF) {
		double dotProduct = 0.0;
        double docNorm = normVector(docTFIDF);

        for (String term : queryTFIDF.keySet()) {
            if (docTFIDF.containsKey(term)) {
                dotProduct += queryTFIDF.get(term) * docTFIDF.get(term);
            }
        }
        return dotProduct;
//        return (queryNorm == 0 || docNorm == 0) ? 0.0 : dotProduct / (queryNorm * docNorm);
	}
	
	
	public double normVector(Map<String, Double> queryTFIDF) {
		double queryNorm = 0.0;
	    for (double val : queryTFIDF.values()) 
	    	queryNorm += val * val;
	    queryNorm = Math.sqrt(queryNorm);
	    return queryNorm;
	}

	
	
}
