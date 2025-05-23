# Nadry+ Search Engine Backend 🔍

<p align="center">
  <strong>Nadry+ Search Engine Backend</strong>
</p>

**Nadry+ Backend** is a robust search engine backend built with Java. It features a multi-threaded web crawler, indexer, and search API that powers the Nadry+ search experience.

---

## ✨ Features

- **🕸️ Web Crawler:** Multi-threaded crawler that efficiently navigates the web
- **🔍 Indexer:** Builds and maintains an inverted index for fast searching
- **🔄 Automatic Resume:** Can restart from previous crawling state if interrupted
- **🤖 Robot Rules Compliance:** Respects robots.txt directives
- **🧠 Content Analysis:** Calculates compact string representations of content to avoid duplicates
- **📚 MongoDB Integration:** Stores crawled pages, URLs, and search indexes
- **🔤 Tokenization:** Processes and stems text for effective searching
- **📊 Page Ranking:** Implements relevance ranking algorithms
- **🔠 Phrase Search:** Supports exact phrase matching with quotation marks
- **📋 Search Suggestions:** Provides search suggestions based on previous queries
- **⚡ RESTful API:** Spring Boot REST endpoints for search and suggestions
- **🌐 Cross-Origin Support:** Configured for integration with frontend applications

---

## 🛠️ Tech Stack

- **Language:** Java
- **Build Tool:** Maven
- **Web Framework:** Spring Boot
- **Database:** MongoDB
- **Web Scraping:** JSoup
- **Text Processing:** Snowball Stemmer
- **Additional Services:** Supabase for suggestions

---

## 🚀 Getting Started

Follow these instructions to get the backend running on your local machine.

### Prerequisites

- Java (JDK 11 or later)
- Maven
- MongoDB (running on localhost:27017)

### Installation & Running

1. **Clone the repository:**
```bash
git clone https://github.com/yourusername/Nadry-Search-Engine-BE.git
cd Nadry-Search-Engine-BE
```
2. **Build the project:**
```bash
mvn clean install
```
3. **Run the web crawler:**
```bash
mvn exec:java -Dexec.mainClass=nadry.webCrawler.WebCrawlerMain -Dexec.jvmArgs="-Xmx2G"
```
4. **Run the indexer:**
```bash
mvn exec:java -Dexec.mainClass=nadry.indexer.IndexerApplication
```
5. **Run the API server:**
```bash
mvn spring-boot:run
```

---

## 📁 Project Structure

<pre>
Nadry-Search-Engine-BE/
├── src/main/java/nadry/
│   ├── api/                 # REST API controllers and services
│   │   ├── SearchApplication.java      # Spring Boot entry point
│   │   ├── SearchController.java       # API endpoints
│   │   ├── SearchWrapper.java          # Search logic wrapper
│   │   └── SupabaseService.java        # External suggestion service
│   ├── webCrawler/          # Web crawling components
│   │   ├── WebCrawler.java            # Main crawler implementation
│   │   ├── WebCrawlerMain.java        # Entry point for crawler
│   │   ├── MongoJava.java             # MongoDB interactions
│   │   └── RobotChecker.java          # Robots.txt compliance
│   ├── indexer/             # Indexing components
│   │   ├── DocumentProcessor.java      # Process HTML documents
│   │   ├── IndexBuilder.java           # Build search index
│   │   ├── InvertedIndex.java          # Core index structure
│   │   ├── Tokenizer.java              # Text tokenization
│   │   └── StopWordFilter.java         # Filter common words
│   ├── ranker/              # Search result ranking
│   │   ├── Ranker.java                # Ranking algorithms
│   │   ├── PageRank.java              # PageRank implementation
│   │   └── QueryDocument.java         # Document representation for ranking
│   └── Config.java          # Global configuration
├── pom.xml                  # Maven project configuration
└── README.md                # Documentation
</pre>

---
