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
import java.util.HashMap;
import java.util.List;

import lombok.Getter;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.stream.Collectors;
import com.example.smartchargebackend.records.PriceData;


public class TibberAPI {

    private static final String TIBBER_API_URL = "https://api.tibber.com/v1-beta/gql";
    private static final String TIBBER_API_TOKEN = System.getenv("TIBBER_API_TOKEN");  // Replace with your API token

    @Getter
    private static List<PriceData> priceList = new ArrayList<>();
    private static final HashMap<String, List<PriceData>> chargingHours = new HashMap<>();

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
            con.setRequestProperty("Authorization", "Bearer " + TIBBER_API_TOKEN);  // Add the Tibber API token for authorization
            con.setDoOutput(true);

            // Build the request body
            JSONObject requestBody = new JSONObject();
            requestBody.put("query", query);

            // Send the request
            OutputStream os = con.getOutputStream();
            os.write(requestBody.toString().getBytes());
            os.flush();
            os.close();

            // Get the response code
            int responseCode = con.getResponseCode();
            System.out.println("Response Code: " + responseCode);

            // Read the response
            if (responseCode == HttpURLConnection.HTTP_OK) {  // Success
                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                // Print the response
                System.out.println("Response: " + response);
                return response.toString();
            } else {
                System.out.println("POST request failed.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // Function to parse and store the API response
    private static List<PriceData> parseResponse(String jsonResponse) {
        List<PriceData> priceList = new ArrayList<>();
        JSONObject data = new JSONObject(jsonResponse);

        // Parse 'today' prices
        JSONArray todayPrices = data.getJSONObject("data")
                .getJSONObject("viewer")
                .getJSONArray("homes")
                .getJSONObject(0)
                .getJSONObject("currentSubscription")
                .getJSONObject("priceInfo")
                .getJSONArray("today");

        // Add today's data to priceList
        parsePrices(todayPrices, priceList);

        // Parse 'tomorrow' prices if available
        JSONArray tomorrowPrices = data.getJSONObject("data")
                .getJSONObject("viewer")
                .getJSONArray("homes")
                .getJSONObject(0)
                .getJSONObject("currentSubscription")
                .getJSONObject("priceInfo")
                .optJSONArray("tomorrow");

        if (tomorrowPrices != null) {
            parsePrices(tomorrowPrices, priceList);
        }

        return priceList;
    }

    // Helper function to parse price array
    private static void parsePrices(JSONArray pricesArray, List<PriceData> priceList) {
        for (int i = 0; i < pricesArray.length(); i++) {
            JSONObject priceInfo = pricesArray.getJSONObject(i);
            double total = priceInfo.getDouble("total");

            // Parse startsAt as OffsetDateTime
            OffsetDateTime startsAt = OffsetDateTime.parse(priceInfo.getString("startsAt"), DateTimeFormatter.ISO_OFFSET_DATE_TIME);

            priceList.add(new PriceData(total, startsAt));
        }
    }

    // Function to get the n cheapest hours
    public static List<PriceData> getCheapestHours(List<PriceData> prices, int n) {
        // Sort prices by the total value (ascending)
        prices.sort(Comparator.comparingDouble(PriceData::total));

        // Return the n cheapest hours
        return prices.subList(0, Math.min(n, prices.size())); // Math.min(n, prices.size()) avoids index ouf of range
    }

    // Function to get the n cheapest hours from now on
    public static List<PriceData> getCheapestHoursFromNow(List<PriceData> prices, int n) {
        // Get the current time and round down to the start of the hour
        OffsetDateTime currentTime = OffsetDateTime.now().truncatedTo(ChronoUnit.HOURS);

        // Filter out prices that start before the current hour
        List<PriceData> filteredPrices = prices.stream()
                .filter(price -> !price.startsAt().isBefore(currentTime)) // Only include future or current times
                .sorted(Comparator.comparingDouble(PriceData::total)) // Sort by price (ascending)
                .collect(Collectors.toList());

        // Return the n cheapest hours starting from the current hour
        return filteredPrices.subList(0, Math.min(n, filteredPrices.size()));
    }

    // Function to get the cheapest hours within a time frame
    public static List<PriceData> getCheapestHoursWithinTimeFrame(List<PriceData> prices, OffsetDateTime fromTime, int untilHours, int n) {
        OffsetDateTime toTime = fromTime.plusHours(untilHours);

        // Filter the prices within the time frame
        List<PriceData> filteredPrices = prices.stream()
                .filter(price -> !price.startsAt().isBefore(fromTime) && !price.startsAt().isAfter(toTime))
                .sorted(Comparator.comparingDouble(PriceData::total)) // Sort by price
                .collect(Collectors.toList());

        return filteredPrices.subList(0, Math.min(n, filteredPrices.size()));
    }

    // Funktion to set charchingHours of an id to the cheapest hours within a time frame
    public static List<PriceData> scheduleChargingHoursForId(String id,OffsetDateTime fromTime, int untilHours, int n) {
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

    // Return the charching ours of an id
    public static List<PriceData> getChargingHours(String id) {
        return chargingHours.get(id);
    }

}
