const express = require("express");
const { dummyResults } = require("../models/data.js");
const { supabase } = require("../models/supabase.js");
// Import phraseSearch along with tokenize and search
const { tokenize, search, phraseSearch } = require("./tokenizer-bridge.js");
const SearchService = require("../services/SearchService.js");
const { v4: uuidv4 } = require("uuid");

// Create a search service instance, passing the tokenizer, search, and phraseSearch functions
const searchService = new SearchService({ tokenize, search, phraseSearch });

const searchCache = new Map();

// Add debug output to verify search and phraseSearch are available
console.log(
  `SearchController: Search function available: ${typeof search === "function"}`
);
console.log(
  `SearchController: PhraseSearch function available: ${
    typeof phraseSearch === "function"
  }`
);

/**
 * Extract phrases between double quotes and remove them from the query.
 * @param {string} query
 * @returns {{ phrases: string[], cleanedQuery: string }}
 */
function extractQuotedPhrases(query) {
  const phrases = [];
  // Match phrases between double quotes
  const regex = /"([^"]+)"/g;
  let match;
  let cleanedQuery = query;
  while ((match = regex.exec(query)) !== null) {
    phrases.push(match[1].replace("'", " ").replace('"', " ").trim());
  }
  // Remove all quoted phrases from the query
  cleanedQuery = query.replace(regex, "").replace(/\s+/g, " ").trim();
  return { phrases, cleanedQuery };
}

/**
 * Basic search endpoint
 * @route GET /api/search
 */
const getSearch = async (req, res) => {
  try {
    const { query, page = 1, limit = 20 } = req.query; // Default limit to 20
    if (!query) {
      return res.status(400).json({
        success: false,
        message: "Search query is required",
      });
    }

    // Check for quoted phrases and extract them
    const quotedInfo = extractQuotedPhrases(query);
    const isPhraseSearch = quotedInfo.phrases.length > 0;
    // Use the first phrase if it exists, otherwise the cleaned query (or original if no quotes)
    const searchQuery = isPhraseSearch
      ? quotedInfo.phrases[0]
      : quotedInfo.cleanedQuery || query;

    const pageNum = parseInt(page, 10) || 1;
    const limitNum = parseInt(limit, 10) || 20; // Ensure limit is a number, default 20

    let serviceResult;
    let searchTimeSec = 0; // Initialize search time

    // Check cache first
    const cachedData = searchCache.get(searchQuery);
    if (cachedData) {
      serviceResult = cachedData.serviceResult;
      searchTimeSec = cachedData.searchTimeSec; // Retrieve stored search time
      console.log(
        `SearchController: Cache HIT for query: "${searchQuery}". Using stored time: ${searchTimeSec}s`
      );
    } else {
      console.log(`SearchController: Cache MISS for query: "${searchQuery}"`);
      // Start timing only if fetching from service
      const searchStart = Date.now();
      console.log(
        `SearchController: Performing ${
          isPhraseSearch ? "PHRASE" : "REGULAR"
        } search for: "${searchQuery}"`
      );

      // Pass the determined search query and the isPhraseSearch flag
      serviceResult = await searchService.search(
        searchQuery,
        pageNum, // pageNum and limitNum might not be needed by service if it returns all results
        limitNum,
        isPhraseSearch
      );

      // End timing
      const searchEnd = Date.now();
      searchTimeSec = (searchEnd - searchStart) / 1000; // in seconds

      // Store the full result set and the search time in the cache
      searchCache.set(searchQuery, { serviceResult, searchTimeSec }); // Store both
      console.log(
        `SearchController: Stored results in cache for query: "${searchQuery}" with time: ${searchTimeSec}s`
      );

      // Log the tokens for debugging (using the result from the service)
      console.log(
        `Search tokenized "${searchQuery}" into:`,
        serviceResult.tokens
      );
      console.log(
        `SearchController: Received ${serviceResult.totalResults} total results from service.`
      );
    }

    const startIndex = (pageNum - 1) * limitNum;
    const endIndex = pageNum * limitNum;
    const resultsArray = Array.isArray(serviceResult.results)
      ? serviceResult.results
      : [];
    const paginatedData = resultsArray.slice(startIndex, endIndex);

    console.log(
      `SearchController: Returning ${paginatedData.length} results for page ${pageNum}.`
    );

    return res.status(200).json({
      success: true,
      data: paginatedData,
      totalPages: Math.ceil(serviceResult.totalResults / limitNum),
      currentPage: pageNum, // Add current page info
      totalResults: serviceResult.totalResults, // Add total results info
      tokens: serviceResult.tokens, // Include tokens in the response
      searchTimeSec,
    });
  } catch (error) {
    console.error("Search error:", error);
    return res.status(500).json({
      success: false,
      message: "An error occurred during search",
      error: error.message, // Include error message for debugging
    });
  }
};

/**
 * Get search suggestions
 * @route GET /api/search/suggestions
 */
const getSuggestions = async (req, res) => {
  try {
    const { query, limit = 5 } = req.query;
    if (!query) {
      return res.status(400).json({
        success: false,
        message: "Query prefix is required",
      });
    }
    const limitNum = parseInt(limit, 10) || 5;

    // Database lookup only, no fallback
    try {
      const { data, error } = await supabase
        .from("Suggestions")
        .select("Suggestions")
        .ilike("Suggestions", `%${query}%`)
        .limit(limitNum);

      console.log("Supabase suggestions data:", data);

      if (error) {
        console.error("Supabase suggestions error:", error);
        throw error;
      }

      // Return the data, even if empty
      const suggestions =
        data && data.length > 0 ? data.map((item) => item.Suggestions) : [];

      return res.status(200).json({
        success: true,
        data: suggestions,
        source: "database",
      });
    } catch (dbError) {
      console.error(
        "Database error when fetching suggestions:",
        dbError.message
      );
      return res.status(500).json({
        success: false,
        message: "Error accessing suggestion database",
        error: dbError.message,
      });
    }
  } catch (error) {
    console.error("Suggestions error:", error);
    return res.status(500).json({
      success: false,
      message: "An error occurred while fetching suggestions",
      error: error.message,
    });
  }
};

/**
 * Save search query to suggestions table
 * @route POST /api/search/save-search
 */
const saveSearch = async (req, res) => {
  try {
    const { query } = req.body; // Get the query from request body

    if (!query || query.trim() === "") {
      return res.status(400).json({
        success: false,
        message: "Search query is required",
      });
    }

    // Check if the query already exists in the Suggestions table
    const { data: existingData, error: checkError } = await supabase
      .from("Suggestions")
      .select("id")
      .eq("Suggestions", query)
      .maybeSingle();

    if (checkError) {
      console.error("Error checking existing query:", checkError);
      throw checkError;
    }

    // If query doesn't exist, save it
    if (!existingData) {
      const { data, error } = await supabase
        .from("Suggestions")
        .insert([{ Suggestions: query }]);

      if (error) {
        console.error("Error saving query:", error);
        throw error;
      }

      console.log(`Saved new search query: "${query}"`);
    } else {
      console.log(`Query "${query}" already exists in database`);
    }

    return res.status(200).json({
      success: true,
      message: "Search query processed successfully",
    });
  } catch (error) {
    console.error("Save search error:", error);
    return res.status(500).json({
      success: false,
      message: "An error occurred while saving the search query",
      error: error.message,
    });
  }
};

module.exports = {
  getSearch,
  getSuggestions,
  saveSearch,
};
