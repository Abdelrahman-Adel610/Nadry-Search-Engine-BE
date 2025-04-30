// Load environment variables at the very beginning
require("dotenv").config({ path: process.env.DOTENV_PATH || "./.env" });
console.log("Environment loaded. NODE_ENV:", process.env.NODE_ENV);

const express = require("express");
const bodyParser = require("body-parser");
const cors = require("cors");

// Import routes
const searchRoutes = require("./routes/searchRoutes");
const searchController = require("./controllers/searchController");

const app = express();

// Middleware
app.use(cors());
app.use(bodyParser.json());
app.use(bodyParser.urlencoded({ extended: true }));

// Add logging middleware for debugging
app.use((req, res, next) => {
  console.log(`${new Date().toISOString()} - ${req.method} ${req.url}`);
  next();
});

// Mount routes
app.use("/api/search", searchRoutes);

// Direct access to suggestions endpoint
app.get("/api/suggestions", searchController.getSuggestions);

// Add a direct route for save-search similar to suggestions
app.post("/api/save-search", searchController.saveSearch);

// Home route
app.get("/", (req, res) => {
  res.json({ message: "Welcome to the JS Node.js Project API" });
});

// Start server
const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(
    `Server is running on port ${PORT} in ${
      process.env.NODE_ENV || "development"
    } mode`
  );
});
