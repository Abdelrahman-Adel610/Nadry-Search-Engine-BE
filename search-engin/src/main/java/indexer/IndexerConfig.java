package indexer;

public class IndexerConfig {

	//static private String  connectionString = "mongodb+srv://salahmostafa3060:Oq7Y8AnTYgoOZcLg@cluster0.dcwx4lg.mongodb.net/?retryWrites=true&w=majority&appName=Cluster0";
	
	
	static private String connectionString = "mongodb+srv://admin:admin@cluster0.wtcajo8.mongodb.net/?retryWrites=true&w=majority&appName=Cluster0";
	static private String databaseName = "search_engine";
	static private String collectionName = "inverted_index";
	static public String getConnectionString()
	{
		return connectionString;
	}
	static public String getDatabaseName()
	{
		return databaseName;
	}
	static public String getCollectionName ()
	{
		return collectionName ;
	}
}

//mongosh mongodb://localhost:27017
