package nadry.ranker;

import java.util.Map;

public class QueryDocument{
	String url;
	Map<String, Integer> termFrequency;
	double popularityScore;
	int totalWord;
	
	public QueryDocument(String url, Map<String, Integer> termFrequency) {
		this.url = url;
		this.termFrequency =termFrequency;
//		this.popularityScore = popularityScore;
	}
	
	public String GetURL() {return url;}
	
	public Map<String, Integer> GetTermFrequency(){
		return termFrequency;
	}
	
	public Double GetPopularityScore() {
		return popularityScore;
	}
	
	public void SetPopularityScore(double score) {
		popularityScore = score;
	}
	
	public int GetTotalWord(){
		return totalWord;
	}
	
	public void SetTotalWordCount(int total) {
		totalWord = total;
	}
}
