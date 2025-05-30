package nadry.ranker;

import java.util.*;

import indexer.DocumentData;
import indexer.MongoDBIndexStore;

public class PageRank {
	private static final String CONNECTION_STRING = "mongodb://localhost:27017/";

    private static final double DAMPING_FACTOR = 0.85;
    private static final double TOLERANCE = 1.0e-6;
    private static final int MAX_ITER = 100;

    public static void main(String[] args) {
        MongoDBIndexStore db = new MongoDBIndexStore(CONNECTION_STRING, "search_engine2", "inverted_index");
        List<DocumentData> docs = db.getAllDocuments();
        
        System.out.println("Loaded Docs: ");
        Set<String> urlsSet = new HashSet<String>();
        for(DocumentData doc : docs) urlsSet.add(doc.getUrl());
        
        Map<String, List<String>> graph = new HashMap<String, List<String>>();
        for(DocumentData doc : docs) {
        	System.out.printf("URL: %s, Links: %d\n", doc.getUrl(), doc.getLinks().size());
        	for(String to : doc.getLinks()) {
        		if(!urlsSet.contains(to)) continue;
        		
        		System.out.printf("\tTo: %s\n", to);
        		List<String> incommingURLs = graph.getOrDefault(to, new ArrayList<String>());
        		incommingURLs.add(doc.getUrl());
        		graph.put(to, incommingURLs);
        	}
        }

        Map<String, Double> ranks = computePageRank(graph);
        for (Map.Entry<String, Double> entry : ranks.entrySet()) {
            System.out.printf("Page %s: %.6f%n", entry.getKey(), entry.getValue());
        }
        
        db.updateDocumentScores(ranks);
    }

    public static Map<String, Double> computePageRank(Map<String, List<String>> graph) {
        int N = graph.size();
        Map<String, Double> ranks = new HashMap<>();
        for (String page : graph.keySet()) {
            ranks.put(page, 1.0 / N);
        }

        Map<String, List<String>> incomingLinks = graph;
//        buildIncomingLinks(graph);

        for (int iter = 0; iter < MAX_ITER; iter++) {
            Map<String, Double> newRanks = new HashMap<>();
            double diff = 0;

            for (String page : graph.keySet()) {
                double rankSum = 0.0;
                List<String> incoming = incomingLinks.getOrDefault(page, new ArrayList<>());

                for (String inPage : incoming) {
                	if(!graph.containsKey(inPage)) {
                		System.out.printf("%s no one links to it.\n", inPage);
                		continue;
                	}
                    int outLinks = graph.get(inPage).size();
                    rankSum += ranks.get(inPage) / outLinks;
                }

                double newRank = (1 - DAMPING_FACTOR) / N + DAMPING_FACTOR * rankSum;
                newRanks.put(page, newRank);
                diff += Math.abs(newRank - ranks.get(page));
            }

            ranks = newRanks;
            if (diff < TOLERANCE) break;
        }
        
        return ranks;
    }

    private static Map<String, List<String>> buildIncomingLinks(Map<String, List<String>> graph) {
        Map<String, List<String>> incoming = new HashMap<>();
        for (String from : graph.keySet()) {
            for (String to : graph.get(from)) {
                incoming.computeIfAbsent(to, k -> new ArrayList<>()).add(from);
            }
        }
        return incoming;
    }
}
