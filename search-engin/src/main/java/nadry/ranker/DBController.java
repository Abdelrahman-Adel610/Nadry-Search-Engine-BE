package nadry.ranker;

import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

import org.bson.*;
import org.bson.conversions.*;


public class DBController {
	private final String CONNECTION_STRING = "mongodb://localhost:27017/";
	
    private MongoClient mongoClient;
    private MongoDatabase database;
    
	public DBController() {
    	mongoClient = MongoClients.create(CONNECTION_STRING);
        database = mongoClient.getDatabase("Nadry");
        

        try {
            // Send a ping to confirm a successful connection
            Bson command = new BsonDocument("ping", new BsonInt64(1));
            Document commandResult = database.runCommand(command);
            System.out.println("Pinged your deployment. You successfully connected to MongoDB!");
        } catch (MongoException me) {
            System.err.println(me);
        }
        

    }
}
