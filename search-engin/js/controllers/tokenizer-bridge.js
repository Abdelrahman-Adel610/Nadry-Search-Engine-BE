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
  // Try importing TokenizerWrapper first (full implementation)
  const TokenizerWrapper = java.import("TokenizerWrapper");
  tokenizerInstance = new TokenizerWrapper();
  tokenizerType = "TokenizerWrapper";
  console.log("Successfully loaded TokenizerWrapper");
} catch (wrapperError) {
  console.warn("Failed to load TokenizerWrapper:", wrapperError.message);

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
      // Java tokenizers use the 'tokenizeSync' method for synchronous calls
      const result = tokenizerInstance.tokenizeSync(text);

      // Convert Java array to JavaScript array if needed
      if (Array.isArray(result)) {
        return result;
      } else if (typeof result.length === "number") {
        const jsArray = [];
        for (let i = 0; i < result.length; i++) {
          jsArray.push(String(result[i]));
        }
        return jsArray;
      }
      return [];
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

console.log(`Tokenizer bridge initialized with ${tokenizerType} tokenizer`);

module.exports = {
  tokenize,
  tokenizerType,
};
