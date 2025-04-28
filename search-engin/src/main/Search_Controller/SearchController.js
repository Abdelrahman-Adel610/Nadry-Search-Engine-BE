const express = require("express");
const { dummyResults } = require("./data.js");
const { supabase } = require("./supabase.js");
const { v4: uuidv4 } = require("uuid");
const router = express.Router();

/**
 * Basic search endpoint
 * @route GET /search
 */
router.get("/search", async (req, res) => {
  try {
    const { query, page, limit = 20 } = req.query;

    if (!query) {
      return res.status(400).json({
        success: false,
        message: "Search query is required",
      });
    }

    // const results = await SearchService.search(query, parseInt(page), parseInt(limit));

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
});

/**
 * Advanced search with filters
 * @route POST /search/advanced
 */
router.post("/advanced", async (req, res) => {
  try {
    const { query, filters, page = 1, limit = 10 } = req.body;

    if (!query) {
      return res.status(400).json({
        success: false,
        message: "Search query is required",
      });
    }

    const results = await SearchService.advancedSearch(
      query,
      filters,
      parseInt(page),
      parseInt(limit)
    );

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
});

/**
 * Get search suggestions
 * @route GET /search/suggestions
 */
router.get("/suggestions", async (req, res) => {
  try {
    const { query, limit = 5 } = req.query;

    if (!query) {
      return res.status(400).json({
        success: false,
        message: "Query prefix is required",
      });
    }
    const { data, error } = await supabase
      .from("Suggestions")
      .select("Suggestions")
      .ilike("Suggestions", `%${query}%`)
      .limit(parseInt(limit));
    console.log(data);

    if (error) {
      return res.status(400).json({
        success: false,
        message: error.message || error.data,
      });
    }
    const suggestions = data.map((item) => item.Suggestions);
    return res.status(200).json({
      success: true,
      data: suggestions,
    });
  } catch (error) {
    console.error("Suggestions error:", error);
    return res.status(500).json({
      success: false,
      message: "An error occurred while fetching suggestions",
    });
  }
});

router.post("/save-search", async (req, res) => {
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
});

module.exports = router;
