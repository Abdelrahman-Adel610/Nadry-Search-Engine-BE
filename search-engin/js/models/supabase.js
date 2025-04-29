// Load environment variables from .env file - use path relative to where the app is started
require('dotenv').config({ path: process.env.DOTENV_PATH || './.env' });
const { createClient } = require("@supabase/supabase-js");

// Get credentials from environment variables with fallbacks
const supabaseUrl = process.env.SUPABASE_URL 
const supabaseKey = process.env.SUPABASE_KEY

const supabase = createClient(supabaseUrl, supabaseKey);

module.exports = { supabase };