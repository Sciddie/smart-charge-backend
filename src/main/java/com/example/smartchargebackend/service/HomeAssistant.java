package com.example.smartchargebackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

@Service
public class HomeAssistant {
    private static final Logger logger = LoggerFactory.getLogger(HomeAssistant.class);
    private final String homeAssistantUrl;
    private final String homeAssistantToken;
    private final TibberAPI tibberAPI;
    private final ObjectMapper objectMapper;

    public HomeAssistant(@Value("${homeassistant.api.url}") String homeAssistantUrl,
                         @Value("${homeassistant.api.token}") String homeAssistantToken,
                         TibberAPI tibberAPI,
                         ObjectMapper objectMapper) {
        this.homeAssistantUrl = homeAssistantUrl;
        this.homeAssistantToken = homeAssistantToken;
        this.tibberAPI = tibberAPI;
        this.objectMapper = objectMapper;
        controlDevides();
        logger.info("Home Assistant service initialized");
    }

    @Scheduled(cron = "0 0 * * * *") // Run every hour
    private void controlDevides() {
        OffsetDateTime currentTime = OffsetDateTime.now().truncatedTo(ChronoUnit.HOURS);
        tibberAPI.getDevices().forEach(device ->
            setState(device, tibberAPI.getChargingHours(device).contains(currentTime))
        );
    }

    private void setState(String entity, boolean state) {
        callApi("{\"entity_id\": \"" + entity + "\"}", "services/switch/turn_" + (state ? "on" : "off"), "POST");
    }

    public ArrayNode getSwitchEntities() {
        ArrayNode entities = callApi(null, "states", "GET");
        if (entities != null) {
            return filterSwichEntities(entities);
        } else {
            return null;
        }
    }

    private ArrayNode callApi(String query, String path, String requestMethod) {
        try {
            URL url = new URL(homeAssistantUrl + path);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod(requestMethod);
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Authorization", "Bearer " + homeAssistantToken);
            con.setDoOutput(true);

            if (query != null) {
                try (OutputStream os = con.getOutputStream()) {
                    byte[] input = query.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
            }

            int responseCode = con.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    logger.info("API call successful. Response received.");
                    return (ArrayNode) objectMapper.readTree(response.toString());
                }
            } else {
                logger.error("{} request failed with code: {}", requestMethod, responseCode);
                return null;
            }
        } catch (Exception e) {
            logger.error("API call failed: {}", e.getMessage(), e);
            return null;
        }
    }

    private ArrayNode filterSwichEntities(ArrayNode entities) {
        ArrayNode resultArray = objectMapper.createArrayNode();
        for (JsonNode entity : entities) {
            String entityId = entity.get("entity_id").asText();
            if (entityId.startsWith("switch.")) {
                // Neues JSON-Objekt für jede Switch-Entität erstellen
                ObjectNode switchEntity = objectMapper.createObjectNode();
                switchEntity.put("entity_id", entityId);
                ObjectNode attributes = objectMapper.createObjectNode();
                attributes.put("friendly_name", entity.path("attributes").path("friendly_name").asText("Kein Friendly Name"));
                switchEntity.set("attributes", attributes);

                // JSON-Objekt dem Array hinzufügen
                resultArray.add(switchEntity);
            }
        } callApi(null, "states", "GET");
        return resultArray;
    }
}
