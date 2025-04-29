package nadry.ranker;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class Ranker {
	
	/*
	 * query Bag is a map that contains frequencies of query phrase
	 * pages Bag is a list of map that contains frequencies of query phrase
	 * @returns sorted pages
	 */
	public static  ArrayList<Map<String, Integer>> Rank(Map<String, Integer> queryBag, ArrayList<Map<String, Integer>> pagesBag) {	
		ArrayList<Double> totalScores = new ArrayList<Double>(pagesBag.size());
		
		ArrayList<Double> relevanceScores = CalculatePopularityScore(queryBag, pagesBag);
		
		ArrayList<Double> popularityScores = new ArrayList<Double>();
		//Todo: CalculatePopularityScore();
		
		
		List<Map.Entry<Double, Map<String, Integer>>> scoredDocs = new ArrayList<>();

	    for (int i = 0; i < pagesBag.size(); i++) {
	        double totalScore = relevanceScores.get(i) * 0.7 + popularityScores.get(i) * 0.3;
	        Map<String, Integer> doc = pagesBag.get(i);
	        scoredDocs.add(new AbstractMap.SimpleEntry<>(totalScore, doc));
	    }

	    // Sort by totalScore descending
	    scoredDocs.sort((a, b) -> Double.compare(b.getKey(), a.getKey()));

	    // Extract sorted documents
	    ArrayList<Map<String, Integer>> sortedDocs = new ArrayList<>();
	    for (Map.Entry<Double, Map<String, Integer>> entry : scoredDocs) {
	        sortedDocs.add(entry.getValue());
	    }

	    return sortedDocs;
	}
		
	/*
	 * query Bag is a map that contains frequencies of query phrase
	 * pages Bag is a list of map that contains frequencies of query phrase
	 */
	public static ArrayList<Double> CalculatePopularityScore(Map<String, Integer> queryBag, ArrayList<Map<String, Integer>> pagesBag) {
	    int N = pagesBag.size(); // total documents

	    // Compute document frequency DF for each term
	    Map<String, Integer> docFreq = new HashMap<>();
	    for (Map<String, Integer> doc : pagesBag) {
	        for (String term : doc.keySet()) {
	            docFreq.put(term, docFreq.getOrDefault(term, 0) + 1);
	        }
	    }

	    // Build query TF-IDF vector
	    Map<String, Double> queryTFIDF = calculateTFIDF(queryBag, docFreq, N);

	    // Normalize query vector
	    double queryNorm = normVector(queryTFIDF);

	    ArrayList<Double> scores = new ArrayList<Double>();
	    // Compare each document with the query
	    for (int i = 0; i < pagesBag.size(); i++) {
	        Map<String, Integer> doc = pagesBag.get(i);

	        // Document TF-IDF
	        Map<String, Double> docTFIDF = calculateTFIDF(doc, docFreq, N);

	        double relevanceScore = dotProduct(queryTFIDF, queryNorm, docTFIDF);
	        System.out.printf("Document %d Popularity: %.4f\n", i + 1, relevanceScore);
	        scores.add(relevanceScore);
	    }
	    return scores;
	}
	
	/*
	 * Calculated TF-IDF vector for a document
	 */
	public static Map<String, Double> calculateTFIDF(Map<String, Integer> doc, Map<String, Integer> docFreq, int TotalDocs) {
		int docLength = 0;
        for (int freq : doc.values()) docLength += freq;

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
	public static double dotProduct(Map<String, Double> queryTFIDF, double queryNorm, Map<String, Double> docTFIDF) {
		double dotProduct = 0.0;
        double docNorm = normVector(docTFIDF);

        for (String term : queryTFIDF.keySet()) {
            if (docTFIDF.containsKey(term)) {
                dotProduct += queryTFIDF.get(term) * docTFIDF.get(term);
            }
        }

        return (queryNorm == 0 || docNorm == 0) ? 0.0 : dotProduct / (queryNorm * docNorm);
	}
	
	
	public static double normVector(Map<String, Double> queryTFIDF) {
		double queryNorm = 0.0;
	    for (double val : queryTFIDF.values()) 
	    	queryNorm += val * val;
	    queryNorm = Math.sqrt(queryNorm);
	    return queryNorm;
	}

	
	
}
