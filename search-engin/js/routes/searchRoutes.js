const express = require("express");
const searchController = require("../controllers/searchController");

const router = express.Router();

// Use proper route paths - remove the "/search" prefix since it's already in the base path
router.get("/", searchController.getSearch);

router.post("/advanced", searchController.postAdvancedSearch);

router.get("/suggestions", searchController.getSuggestions);

router.post("/save-search", searchController.saveSearch);

module.exports = router;
