package com.example.smartchargebackend.service;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;
import com.example.smartchargebackend.records.PriceData;
import lombok.Getter;

public class TibberAPI {

    private static final String TIBBER_API_URL = "https://api.tibber.com/v1-beta/gql";
    private static final String TIBBER_API_TOKEN = System.getenv("TIBBER_API_TOKEN");  // Use environment variable
    private static final Logger logger = Logger.getLogger(TibberAPI.class.getName());

    @Getter
    private static List<PriceData> priceList = new ArrayList<>();
    private static final ConcurrentHashMap<String, List<PriceData>> chargingHours = new ConcurrentHashMap<>();

    static {
        String apiResponse = callAPI();
        if (apiResponse != null) {
            priceList = parseResponse(apiResponse);
        }
    }

    // Private constructor to prevent instantiation
    private TibberAPI() {
        // No instance should be created
    }

    private static String callAPI() {
        try {
            // Prepare the GraphQL query
            String query = "{ viewer { homes { currentSubscription { priceInfo { today { total startsAt } tomorrow { total startsAt } } } } } }";

            // Create the HTTP connection
            URL url = new URL(TIBBER_API_URL);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Authorization", "Bearer " + TIBBER_API_TOKEN);
            con.setDoOutput(true);

            // Build the request body
            JSONObject requestBody = new JSONObject();
            requestBody.put("query", query);

            // Send the request
            try (OutputStream os = con.getOutputStream()) {
                os.write(requestBody.toString().getBytes());
                os.flush();
            }

            // Get the response code
            int responseCode = con.getResponseCode();
            logger.info("Response Code: " + responseCode);

            // Read the response
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String inputLine;
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    logger.info("Response: " + response);
                    return response.toString();
                }
            } else {
                logger.severe("POST request failed with code: " + responseCode);
            }
        } catch (Exception e) {
            logger.severe("API call failed: " + e.getMessage());
        }
        return null;
    }

    // Function to parse and store the API response
    private static List<PriceData> parseResponse(String jsonResponse) {
        List<PriceData> priceList = new ArrayList<>();
        JSONObject data = new JSONObject(jsonResponse);
        JSONObject priceInfo = data.optJSONObject("data")
                .optJSONObject("viewer")
                .optJSONArray("homes")
                .optJSONObject(0)
                .optJSONObject("currentSubscription")
                .optJSONObject("priceInfo");

        parsePrices(priceInfo.optJSONArray("today"), priceList);
        parsePrices(priceInfo.optJSONArray("tomorrow"), priceList);

        return priceList;
    }

    // Helper function to parse price array
    private static void parsePrices(JSONArray pricesArray, List<PriceData> priceList) {
        if (pricesArray != null) {
            for (int i = 0; i < pricesArray.length(); i++) {
                JSONObject priceInfo = pricesArray.getJSONObject(i);
                double total = priceInfo.getDouble("total");
                OffsetDateTime startsAt = OffsetDateTime.parse(priceInfo.getString("startsAt"), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                priceList.add(new PriceData(total, startsAt));
            }
        }
    }

    // Function to get the n cheapest hours
    public static List<PriceData> getCheapestHours(List<PriceData> prices, int n) {
        // Sort prices by the total value (ascending)
        prices.sort(Comparator.comparingDouble(PriceData::total));
        return prices.subList(0, Math.min(n, prices.size()));
    }

    // Function to get the n cheapest hours from now on
    public static List<PriceData> getCheapestHoursFromNow(List<PriceData> prices, int n) {
        OffsetDateTime currentTime = OffsetDateTime.now().truncatedTo(ChronoUnit.HOURS);
        return prices.stream()
                .filter(price -> !price.startsAt().isBefore(currentTime))
                .sorted(Comparator.comparingDouble(PriceData::total))
                .collect(Collectors.toList())
                .subList(0, Math.min(n, prices.size()));
    }

    // Function to get the cheapest hours within a time frame
    public static List<PriceData> getCheapestHoursWithinTimeFrame(List<PriceData> prices, OffsetDateTime fromTime, int untilHours, int n) {
        OffsetDateTime toTime = fromTime.plusHours(untilHours);
        return prices.stream()
                .filter(price -> !price.startsAt().isBefore(fromTime) && !price.startsAt().isAfter(toTime))
                .sorted(Comparator.comparingDouble(PriceData::total))
                .collect(Collectors.toList())
                .subList(0, Math.min(n, prices.size()));
    }

    // Funktion to set charchingHours of an id to the cheapest hours within a time frame
    public static List<PriceData> scheduleChargingHoursForId(String id, OffsetDateTime fromTime, int untilHours, int n) {
        List<PriceData> cheapestHoursWithinTimeFrame = getCheapestHoursWithinTimeFrame(priceList, fromTime, untilHours, n);
        chargingHours.put(id, cheapestHoursWithinTimeFrame);
        return cheapestHoursWithinTimeFrame;
    }

    // Funktion to set charchingHours of an id to the cheapest hours from now on
    public static List<PriceData> scheduleChargingHoursForId(String id, int n) {
        List<PriceData> cheapestHours = getCheapestHoursFromNow(priceList, n);
        chargingHours.put(id, cheapestHours);
        return cheapestHours;
    }

    public static List<PriceData> getChargingHours(String id) {
        return chargingHours.get(id);
    }
}
