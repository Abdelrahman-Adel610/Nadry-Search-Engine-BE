const express = require("express");
const bodyParser = require("body-parser");
const cors = require("cors");

// Import the search controller
const searchController = require("./main/Search_Controller/SearchController");

// Create Express app
const app = express();

// Middleware
app.use(cors());
app.use(bodyParser.json());
app.use(bodyParser.urlencoded({ extended: true }));

// Mount the search controller with the correct prefix
app.use("/api", searchController);

// Simple home route
app.get("/", (req, res) => {
  res.json({ message: "Welcome to Nadry Search Engine API" });
});

// Start server
const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`Server is running on port ${PORT}`);
});
