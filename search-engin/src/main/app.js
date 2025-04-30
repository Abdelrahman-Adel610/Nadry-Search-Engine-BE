const express = require("express");
const searchController = require("./Search_Controller/SearchController");
// ...existing code...

const app = express();

// Middleware
app.use(express.json());
// ...existing code...

// Mount routes with the /api prefix
app.use("/api", searchController);

// ...existing code...

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`Server running on port ${PORT}`);
});

// ...existing code...
