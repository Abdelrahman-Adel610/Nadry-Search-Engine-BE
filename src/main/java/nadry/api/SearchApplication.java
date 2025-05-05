package nadry.api;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
@SpringBootApplication(exclude = {MongoAutoConfiguration.class})
public class SearchApplication {
    public static void main(String[] args) {
        System.out.println("Starting Search Application...");
        SpringApplication.run(SearchApplication.class, args);
    }
}