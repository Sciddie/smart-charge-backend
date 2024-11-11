package com.example.smartchargebackend.service;

import com.example.smartchargebackend.records.PriceData;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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
    private final ObjectMapper objectMapper;

    @Getter
    private List<PriceData> priceList = new ArrayList<>();
    private final ConcurrentHashMap<String, List<PriceData>> chargingHours = new ConcurrentHashMap<>();

    public TibberAPI(@Value("${tibber.api.url}") String tibberApiUrl,
                     @Value("${tibber.api.token}") String tibberApiToken,
                     ObjectMapper objectMapper) {
        this.tibberApiUrl = tibberApiUrl;
        this.tibberApiToken = tibberApiToken;
        this.objectMapper = objectMapper;
        loadChargingHoursFromFile();
        updatePriceList();
    }

    @Scheduled(cron = "0 0 15 * * *") // Run every day at 3:00 PM
    public void updatePriceList() {
        logger.info("Updating price list from Tibber API");
        String apiResponse = callAPI();
        if (apiResponse != null) {
            try {
                priceList = parseResponse(apiResponse);
                logger.info("Price list updated successfully");
            } catch (IOException e) {
                logger.error("Failed to parse API response", e);
            }
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

            String jsonInputString = "{\"query\": \"" + query + "\"}";

            try (OutputStream os = con.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = con.getResponseCode();
            logger.info("Response Code: {}", responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
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

    private List<PriceData> parseResponse(String jsonResponse) throws IOException {
        JsonNode root = objectMapper.readTree(jsonResponse);
        JsonNode priceInfo = root.path("data").path("viewer").path("homes").get(0)
                .path("currentSubscription").path("priceInfo");

        List<PriceData> priceList = new ArrayList<>();
        parsePrices(priceInfo.path("today"), priceList);
        parsePrices(priceInfo.path("tomorrow"), priceList);

        return priceList;
    }

    private void parsePrices(JsonNode pricesArray, List<PriceData> priceList) {
        for (JsonNode priceInfo : pricesArray) {
            double total = priceInfo.path("total").asDouble();
            OffsetDateTime startsAt = OffsetDateTime.parse(priceInfo.path("startsAt").asText(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            priceList.add(new PriceData(total, startsAt));
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

    private void saveChargingHoursToFile() {
        try (FileWriter file = new FileWriter(FILE_PATH)) {
            objectMapper.writeValue(file, chargingHours);
            logger.info("Charging hours saved to file.");
        } catch (IOException e) {
            logger.error("Error saving charging hours to file: {}", e.getMessage(), e);
        }
    }

    private void loadChargingHoursFromFile() {
        File file = new File(FILE_PATH);
        if (!file.exists()) {
            logger.info("No saved charging hours file found.");
            return;
        }
        try {
            chargingHours.putAll(objectMapper.readValue(file, new TypeReference<ConcurrentHashMap<String, List<PriceData>>>() {}));
            logger.info("Charging hours loaded from file.");
        } catch (IOException e) {
            logger.error("Error loading charging hours from file: {}", e.getMessage(), e);
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
