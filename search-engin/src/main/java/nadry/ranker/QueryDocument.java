package nadry.ranker;

import java.util.Map;

public class QueryDocument{
	String url;
	Map<String, Integer> termFrequency;
	double popularityScore;
	int totalWord;
	double score; 
	String title; 
	String description; 

	public QueryDocument(String url, Map<String, Integer> termFrequency) {
		this.url = url;
		this.termFrequency =termFrequency;
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

	public double getScore() {
		return score;
	}

	public void setScore(double score) {
		this.score = score;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	@Override
	public String toString() {
		return "QueryDocument{" +
				"url='" + url + '\'' +
				", title='" + title + '\'' + // Added title
				", description='" + description + '\'' + // Added description
				", termFrequency=" + termFrequency +
				", popularityScore=" + popularityScore +
				", totalWord=" + totalWord +
				", score=" + score +
				'}';
	}
}
