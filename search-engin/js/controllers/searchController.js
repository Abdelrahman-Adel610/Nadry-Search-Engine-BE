const express = require("express");
const { dummyResults } = require("../models/data.js");
const { supabase } = require("../models/supabase.js");
const { tokenize } = require("./tokenizer-bridge.js");
const SearchService = require("../services/SearchService.js");
const { v4: uuidv4 } = require("uuid");

// Create a search service instance, passing the tokenizer
const searchService = new SearchService({ tokenize });

/**
 * Basic search endpoint
 * @route GET /api/search
 */
const getSearch = async (req, res) => {
  try {
    const { query, page = 1, limit = 20 } = req.query;
    if (!query) {
      return res.status(400).json({
        success: false,
        message: "Search query is required",
      });
    }
    const pageNum = parseInt(page, 10) || 1;
    const limitNum = parseInt(limit, 10) || 20;

    // Call the search service instance method
    const results = await searchService.search(query, pageNum, limitNum);

    // // Log the tokens for debugging
    console.log(`Search tokenized "${query}" into:`, results.tokens);

    return res.status(200).json({
      success: true,
      data: dummyResults.slice((page - 1) * limit, page * limit),
      totalPages: Math.ceil(dummyResults.length / limit),
    });
  } catch (error) {
    console.error("Search error:", error);
    return res.status(500).json({
      success: false,
      message: "An error occurred during search",
    });
  }
};

/**
 * Advanced search with filters
 * @route POST /api/search/advanced
 */
const postAdvancedSearch = async (req, res) => {
  try {
    const { query, filters, page = 1, limit = 10 } = req.body;
    if (!query) {
      return res.status(400).json({
        success: false,
        message: "Search query is required",
      });
    }
    const pageNum = parseInt(page, 10) || 1;
    const limitNum = parseInt(limit, 10) || 10;

    // Call the search service instance method
    const results = await searchService.advancedSearch(
      query,
      filters,
      pageNum,
      limitNum
    );

    // Log the tokens for debugging
    console.log(`Advanced search tokenized "${query}" into:`, results.tokens);

    return res.status(200).json({
      success: true,
      data: results,
    });
  } catch (error) {
    console.error("Advanced search error:", error);
    return res.status(500).json({
      success: false,
      message: "An error occurred during advanced search",
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
  postAdvancedSearch,
  getSuggestions,
  saveSearch,
};
