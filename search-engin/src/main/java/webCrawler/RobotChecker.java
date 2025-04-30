package webCrawler;
import crawlercommons.robots.BaseRobotsParser;
import crawlercommons.robots.SimpleRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;


import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup; 

import java.io.IOException; // Added IO import
import java.net.MalformedURLException; // Added URL import
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap; // Added Map import
import java.util.Map;

public class RobotChecker {

    private static final String MY_USER_AGENT = "Ndry/1.0";
    public static boolean isUrlAllowed(String urlToCheck) {
        try {
            URL url = new URL(urlToCheck);
            String host = url.getHost().toLowerCase();
            String protocol = url.getProtocol();
            int port = url.getPort() != -1 ? url.getPort() : url.getDefaultPort();
            String hostKey = protocol + "://" + host + ":" + port; // key to get the robots rules

            SimpleRobotRules rules = getRobotRules(hostKey);

            if (rules == null) {
                 System.err.println("something went wrong for" + hostKey + ", disallowing for safety.");
                 return false;
            }

            boolean allowed = rules.isAllowed(urlToCheck);
//            System.out.println("Robots check for " + urlToCheck + ": " + (allowed ? "Allowed" : "Disallowed"));
            return allowed;

        }catch (Exception e) {
            System.err.println("Unexpected error checking robots with crawler-commons for " + urlToCheck + ": " + e.getMessage());
            return false;
        }
    }
    private static SimpleRobotRules getRobotRules(String hostKey) {

//        System.out.println("Fetching robots.txt for: " + hostKey);
        String robotsUrl = hostKey + "/robots.txt";
        byte[] robotsBytes = fetchRobotsTxtBytesWithJsoup(robotsUrl);

        SimpleRobotRules rules;
        if (robotsBytes != null) {
            // using the crawler-commons parser
            BaseRobotsParser parser = new SimpleRobotRulesParser();
            rules = (SimpleRobotRules) parser.parseContent(robotsUrl, robotsBytes, "text/plain", MY_USER_AGENT);

        } else {
            // if doesn't exist, allow all
            rules = new SimpleRobotRules(SimpleRobotRules.RobotRulesMode.ALLOW_ALL);
        }

        return rules;
    }

    private static byte[] fetchRobotsTxtBytesWithJsoup(String robotsUrl) {
        try {
            Connection.Response response = Jsoup.connect(robotsUrl)	// fetching robot.txt
                    .userAgent(MY_USER_AGENT)
                    .timeout(5000)
                    .ignoreContentType(true)
                    .followRedirects(true)
                    .execute();

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.bodyAsBytes();
            } else if (response.statusCode() == 404 || response.statusCode() == 410 || response.statusCode() == 403 || response.statusCode() == 401) {
            	// returned empty array to say that it's a fetching error
                return new byte[0];
            } else {
            	// if null, all is allowed
                return null;
            }
        } catch (HttpStatusException e) {
             // Catch specific HTTP status errors Jsoup might throw
             if (e.getStatusCode() == 404 || e.getStatusCode() == 410 || e.getStatusCode() == 403 || e.getStatusCode() == 401) {
                 System.out.println("robots.txt returned " + e.getStatusCode() + " (HttpStatusException) for " + robotsUrl + " -> allowing all.");
                 return new byte[0]; // Treat as allow all
             } else {
                 System.err.println("HTTP error fetching robots.txt from " + robotsUrl + ": " + e.getStatusCode() + " " + e.getMessage() + ". Disallowing for safety.");
                 return null;
             }
        } catch (IOException e) {
        	// for networks errors or timeouts
            System.err.println("Network error fetching robots.txt from " + robotsUrl + ": " + e.getMessage() + ". Disallowing for safety.");
            return null;
        } catch (Exception e) {
             System.err.println("Unexpected error fetching robots.txt from " + robotsUrl + ": " + e.getMessage() + ". Disallowing for safety.");
             return null;
        }
    }

}