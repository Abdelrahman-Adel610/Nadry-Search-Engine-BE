class SearchService {
  constructor(options = {}) {
    this.tokenize = options.tokenize || ((text) => text.split(" "));
    // Get search function if provided, otherwise create empty array fallback
    this.search_java = options.search || ((query) => []);
  }

  async search(query, page, limit) {
    console.log(`SearchService: Calling Java search for query "${query}"`);

    // Use Java search function instead of mock implementation
    const searchResults = this.search_java(query); // This calls tokenizer-bridge.search
    console.log(
      `SearchService: Received ${searchResults.length} results from Java search engine`
    );
    // Log the actual results received for debugging
    console.log("SearchService: Actual results received:", searchResults);

    // Define tokens by calling the tokenizer
    const tokens = this.tokenize(query);

    return {
      results: searchResults, // The actual results from Java
      totalResults: searchResults.length,
      page,
      limit,
      tokens, // Now defined
    };
  }

  async advancedSearch(query, filters, page, limit) {
    // Use Java search function for advanced search too
    console.log(
      `SearchService: Calling Java search for advanced query "${query}" with filters`,
      filters
    );

    const searchResults = this.search_java(query);
    console.log(
      `SearchService: Received ${searchResults.length} results from Java search engine`
    );

    // Still tokenize for compatibility/logging
    const tokens = this.tokenize(query);

    // Apply filters if needed (you may want to implement filter logic here)
    let filteredResults = searchResults;

    return {
      results: [
        `Advanced search for "${query}" with filters (page ${page}, limit ${limit})`,
        `Applied filters: ${JSON.stringify(filters)}`,
      ],
      totalResults: 50,
      page,
      limit,
      tokens,
    };
  }
}

module.exports = SearchService;
