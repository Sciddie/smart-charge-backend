package com.example.smartchargebackend.service;

import com.example.smartchargebackend.records.PriceData;
import lombok.Getter;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class TibberAPI {

    private static final Logger logger = LoggerFactory.getLogger(TibberAPI.class);
    private static final String FILE_PATH = "chargingHours.json";

    private final String tibberApiUrl;
    private final String tibberApiToken;

    @Getter
    private List<PriceData> priceList = new ArrayList<>();
    private final ConcurrentHashMap<String, List<PriceData>> chargingHours = new ConcurrentHashMap<>();

    public TibberAPI(@Value("${tibber.api.url}") String tibberApiUrl,
                     @Value("${tibber.api.token}") String tibberApiToken) {
        this.tibberApiUrl = tibberApiUrl;
        this.tibberApiToken = tibberApiToken;
        loadChargingHoursFromFile();
        updatePriceList();
    }

    @Scheduled(cron = "0 0 15 * * *") // Run every day at 3:00 PM
    public void updatePriceList() {
        logger.info("Updating price list from Tibber API");
        String apiResponse = callAPI();
        if (apiResponse != null) {
            priceList = parseResponse(apiResponse);
            logger.info("Price list updated successfully");
        } else {
            logger.error("Failed to update price list from Tibber API");
        }
    }

    private String callAPI() {
        try {
            String query = "{ viewer { homes { currentSubscription { priceInfo { today { total startsAt } tomorrow { total startsAt } } } } } }";

            URL url = new URL(tibberApiUrl);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Authorization", "Bearer " + tibberApiToken);
            con.setDoOutput(true);

            JSONObject requestBody = new JSONObject();
            requestBody.put("query", query);

            try (OutputStream os = con.getOutputStream()) {
                os.write(requestBody.toString().getBytes());
                os.flush();
            }

            int responseCode = con.getResponseCode();
            logger.info("Response Code: {}", responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String inputLine;
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    logger.info("Response: {}", response);
                    return response.toString();
                }
            } else {
                logger.error("POST request failed with code: {}", responseCode);
            }
        } catch (Exception e) {
            logger.error("API call failed: {}", e.getMessage(), e);
        }
        return null;
    }

    // Function to parse and store the API response
    private List<PriceData> parseResponse(String jsonResponse) {
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
    private void parsePrices(JSONArray pricesArray, List<PriceData> priceList) {
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
    public List<PriceData> getCheapestHours(int n) {
        // Sort prices by the total value (ascending)
        priceList.sort(Comparator.comparingDouble(PriceData::total));
        return priceList.subList(0, Math.min(n, priceList.size()));
    }

    // Function to get the n cheapest hours from now on
    public List<PriceData> getCheapestHoursFromNow(List<PriceData> prices, int n) {
        OffsetDateTime currentTime = OffsetDateTime.now().truncatedTo(ChronoUnit.HOURS);
        return prices.stream()
                .filter(price -> !price.startsAt().isBefore(currentTime))
                .sorted(Comparator.comparingDouble(PriceData::total))
                .collect(Collectors.collectingAndThen(Collectors.toList(), list -> list.subList(0, Math.min(n, list.size()))));
    }

    // Function to get the cheapest hours within a time frame
    public List<PriceData> getCheapestHoursWithinTimeFrame(List<PriceData> prices, OffsetDateTime fromTime, int untilHours, int n) {
        OffsetDateTime toTime = fromTime.plusHours(untilHours);
        return prices.stream()
                .filter(price -> !price.startsAt().isBefore(fromTime) && !price.startsAt().isAfter(toTime))
                .sorted(Comparator.comparingDouble(PriceData::total))
                .collect(Collectors.collectingAndThen(Collectors.toList(), list -> list.subList(0, Math.min(n, list.size()))));
    }

    // Helper function to save charging hours to file after update
    private void saveAndReturnChargingHours(String id, List<PriceData> cheapestHours) {
        chargingHours.put(id, cheapestHours);
        saveChargingHoursToFile(); // Save charging hours to file after updating
    }

    // Funktion to set charchingHours of an id to the cheapest hours within a time frame
    public List<PriceData> scheduleChargingHoursForId(String id, OffsetDateTime fromTime, int untilHours, int n) {
        List<PriceData> cheapestHoursWithinTimeFrame = getCheapestHoursWithinTimeFrame(priceList, fromTime.truncatedTo(ChronoUnit.HOURS), untilHours, n);
        saveAndReturnChargingHours(id, cheapestHoursWithinTimeFrame);
        return cheapestHoursWithinTimeFrame;
    }

    // Funktion to set charchingHours of an id to the cheapest hours from now on within a time frame
    public List<PriceData> scheduleChargingHoursForId(String id, int untilHours, int n) {
        List<PriceData> cheapestHoursWithinTimeFrame = getCheapestHoursWithinTimeFrame(priceList, OffsetDateTime.now().truncatedTo(ChronoUnit.HOURS), untilHours, n);
        saveAndReturnChargingHours(id, cheapestHoursWithinTimeFrame);
        return cheapestHoursWithinTimeFrame;
    }

    // Funktion to set charchingHours of an id to the cheapest hours from now on
    public List<PriceData> scheduleChargingHoursForId(String id, int n) {
        List<PriceData> cheapestHours = getCheapestHoursFromNow(priceList, n);
        saveAndReturnChargingHours(id, cheapestHours);
        return cheapestHours;
    }

    // This method allows you to retrieve charging hours for a specific id
    public List<PriceData> getChargingHours(String id) {
        return chargingHours.get(id);
    }

    // Save charging hours to a file (JSON format)
    private void saveChargingHoursToFile() {
        try (FileWriter file = new FileWriter(FILE_PATH)) {
            JSONObject jsonObject = new JSONObject();
            for (String id : chargingHours.keySet()) {
                JSONArray jsonArray = new JSONArray();
                for (PriceData priceData : chargingHours.get(id)) {
                    JSONObject priceJson = new JSONObject();
                    priceJson.put("total", priceData.total());
                    priceJson.put("startsAt", priceData.startsAt().toString());
                    jsonArray.put(priceJson);
                }
                jsonObject.put(id, jsonArray);
            }
            file.write(jsonObject.toString());
            logger.info("Charging hours saved to file.");
        } catch (IOException e) {
            logger.error("Error saving charging hours to file: " + e.getMessage());
        }
    }

    // Load charging hours from a file (JSON format)
    private void loadChargingHoursFromFile() {
        File file = new File(FILE_PATH);
        if (!file.exists()) {
            logger.info("No saved charging hours file found.");
            return;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(FILE_PATH))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            JSONObject jsonObject = new JSONObject(sb.toString());
            for (String id : jsonObject.keySet()) {
                JSONArray jsonArray = jsonObject.getJSONArray(id);
                List<PriceData> priceDataList = new ArrayList<>();
                parsePrices(jsonArray, priceDataList);
                chargingHours.put(id, priceDataList);
            }
            logger.info("Charging hours loaded from file.");
        } catch (IOException e) {
            logger.error("Error loading charging hours from file: " + e.getMessage());
        }
    }

    public List<String> getDevices() {
        return new ArrayList<>(chargingHours.keySet());
    }

    public void removeDevice(String id) {
        chargingHours.remove(id);
        saveChargingHoursToFile();
    }
}
