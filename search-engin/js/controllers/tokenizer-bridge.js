const path = require("path");
const fs = require("fs");

// Force the module loader to prioritize our local node_modules
process.env.NODE_PATH = path.resolve(__dirname, "../../node_modules");
require("module").Module._initPaths();

// Import java
const java = require("java");

// Add the main JAR
const mainJarPath = path.resolve(
  __dirname,
  "../../target/web-crawler-app-1.0.0.jar"
);
if (fs.existsSync(mainJarPath)) {
  java.classpath.push(mainJarPath);
  console.log("Added main JAR to classpath:", mainJarPath);
} else {
  console.warn(`Main JAR does not exist: ${mainJarPath}`);
}

// Add the compiled classes directory directly
const classesPath = path.resolve(__dirname, "../../target/classes");
if (fs.existsSync(classesPath)) {
  java.classpath.push(classesPath);
  console.log("Added classes directory to classpath:", classesPath);
} else {
  console.warn(`Classes directory does not exist: ${classesPath}`);
}

// Add dependency JARs
const dependencyPaths = [path.resolve(__dirname, "../../target/dependency")];

dependencyPaths.forEach((dependencyPath) => {
  if (fs.existsSync(dependencyPath)) {
    try {
      const files = fs.readdirSync(dependencyPath);
      files.forEach((file) => {
        if (file.endsWith(".jar")) {
          java.classpath.push(path.join(dependencyPath, file));
        }
      });
      console.log(
        `Added ${files.length} dependency JARs from: ${dependencyPath}`
      );
    } catch (error) {
      console.error(
        `Error reading dependency directory ${dependencyPath}:`,
        error
      );
    }
  } else {
    console.warn(`Dependency path does not exist: ${dependencyPath}`);
  }
});

// Initialize tokenizer instance
let tokenizerInstance = null;
let tokenizerType = "fallback";

try {
  // Try importing SearchWrapper using its fully qualified name
  const SearchWrapper = java.import("api.SearchWrapper"); // Changed from indexer.TokenizerWrapper to api.SearchWrapper
  tokenizerInstance = new SearchWrapper();
  tokenizerType = "JavaSearchWrapper"; // Update type name for clarity
  console.log("Successfully loaded api.SearchWrapper");
} catch (wrapperError) {
  console.warn(
    "Failed to load api.SearchWrapper:", // Updated error message
    wrapperError.message
  );
  // Log the full error for better debugging
  console.error(wrapperError);

  // Create a JavaScript-based fallback
  tokenizerInstance = {
    tokenize: function (text) {
      return text
        ? text
            .toLowerCase()
            .split(/[\s,.:;?!-]+/)
            .filter(Boolean)
        : [];
    },
  };
  console.log("Using JavaScript fallback tokenizer");
}

/**
 * Tokenize a string using the available tokenizer
 * @param {string} text - The text to tokenize
 * @returns {string[]} - Array of tokens
 */
function tokenize(text) {
  if (!text || typeof text !== "string") {
    console.warn(`Invalid text provided to tokenizer: ${text}`);
    return [];
  }

  try {
    // Call the appropriate method based on tokenizer type
    if (tokenizerType === "fallback") {
      return tokenizerInstance.tokenize(text);
    } else {
      // Java tokenizers use the 'tokenize' method which now returns String[]
      const javaArray = tokenizerInstance.tokenizeSync(text);

      // Convert Java String[] to JavaScript array
      const jsArray = [];
      for (let i = 0; i < javaArray.length; i++) {
        jsArray.push(String(javaArray[i])); // Ensure conversion to JS string
      }
      return jsArray;
    }
  } catch (error) {
    // Log the actual error for debugging
    console.error(`Error in ${tokenizerType} tokenizer:`, error);

    // Fallback to simple tokenization if the Java method fails
    console.log("Using emergency fallback tokenization for:", text);
    return text
      ? text
          .toLowerCase()
          .split(/[\s,.:;?!-]+/)
          .filter(Boolean)
      : [];
  }
}

/**
 * Search for documents matching the query using the Java search engine
 * @param {string} query - The search query
 * @returns {Array} - Array of search results with document URLs and relevance scores
 */
function search(query) {
  if (!query || typeof query !== "string") {
    console.warn(`Invalid query provided to search: ${query}`);
    return [];
  }

  try {
    // Make sure we're using the Java SearchWrapper
    if (tokenizerType === "JavaSearchWrapper") {
      console.log(
        `tokenizer-bridge: Calling Java searchSync for query "${query}"`
      );
      // Call the search method from the Java SearchWrapper
      const javaResults = tokenizerInstance.searchSync(query);

      // --- Debugging ---
      console.log("tokenizer-bridge: Raw javaResults:", javaResults);
      if (javaResults && typeof javaResults.sizeSync === "function") {
        // Changed size to sizeSync
        console.log(
          "tokenizer-bridge: javaResults.sizeSync():",
          javaResults.sizeSync()
        ); // Changed size() to sizeSync()
      } else {
        console.log(
          "tokenizer-bridge: javaResults does not have a sizeSync method or is null/undefined."
        );
      }
      // --- End Debugging ---

      // Convert Java search results to JavaScript array
      const jsResults = [];
      // Check if javaResults is valid and has sizeSync method before iterating
      if (javaResults && typeof javaResults.sizeSync === "function") {
        // Changed size to sizeSync
        const size = javaResults.sizeSync(); // Store size as a local variable
        for (let i = 0; i < size; i++) {
          const javaResult = javaResults.getSync(i); // Changed get(i) to getSync(i)

          // Convert Java Map to JavaScript object
          const result = {};
          // Check if javaResult is valid and has keySetSync method
          if (javaResult && typeof javaResult.keySetSync === "function") {
            // Changed keySet to keySetSync
            const keysSet = javaResult.keySetSync(); // Get the key set
            const keys = keysSet.toArraySync(); // Changed toArray() to toArraySync()
            for (let j = 0; j < keys.length; j++) {
              const key = String(keys[j]);
              const value = javaResult.getSync(keys[j]); // Changed get(keys[j]) to getSync(keys[j])
              // Handle potential nested Java objects/arrays if necessary
              // For now, assume simple values or let the bridge handle conversion
              result[key] = value;
            }
            jsResults.push(result);
          } else {
            console.warn(
              `tokenizer-bridge: Invalid javaResult at index ${i}:`,
              javaResult
            );
          }
        }
      } else {
        console.warn(
          "tokenizer-bridge: Received invalid javaResults object from searchSync:",
          javaResults
        );
      }

      console.log(
        `tokenizer-bridge: Converted ${jsResults.length} results for query "${query}"`
      );
      return jsResults;
    } else {
      console.warn(
        "Search functionality requires JavaSearchWrapper, but using fallback tokenizer"
      );
      return [];
    }
  } catch (error) {
    console.error(`Error in tokenizer-bridge search:`, error);
    return []; // Return empty array on error
  }
}

console.log(`Tokenizer bridge initialized with ${tokenizerType} tokenizer`);

module.exports = {
  tokenize,
  search,
  tokenizerType,
};
