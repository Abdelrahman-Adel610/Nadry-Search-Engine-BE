package api;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
@RestController
public class SearchController {
    @GetMapping("/test")
    public String test() {
        return "Test endpoint working!";
    }

    // @GetMapping("/search")
    // public String search() {

    //     SearchWrapper searchWrapper = new SearchWrapper();
    //     // searchWrapper.setSearchType("test");
    //     return "Search endpoint working!";
    // }

    @GetMapping("/search")
    public List<Map<String, Object>> search(@RequestParam String query) {
        SearchWrapper searchWrapper = new SearchWrapper();
        searchWrapper.search(query); // or call your actual search method
    
        //  Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> results = new ArrayList<Map<String,Object>>();
         return searchWrapper.search(query);
    }

}