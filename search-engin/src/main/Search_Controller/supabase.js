const { createClient } = require("@supabase/supabase-js");

const supabaseUrl = "https://jfznfxwatpxwqoasszgh.supabase.co";
const supabaseKey =
  "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Impmem5meHdhdHB4d3FvYXNzemdoIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDU4Mzg1NzIsImV4cCI6MjA2MTQxNDU3Mn0._8mpyWZ43SQcgFV6LLtxQpdVLfbqEQ2sf0XcYpeU6sw";

const supabase = createClient(supabaseUrl, supabaseKey);

module.exports = { supabase };
