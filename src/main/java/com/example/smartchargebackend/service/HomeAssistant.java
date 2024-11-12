package com.example.smartchargebackend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

@Service
public class HomeAssistant {
    private static final Logger logger = LoggerFactory.getLogger(HomeAssistant.class);
    private final String homeAssistantUrl;
    private final String homeAssistantToken;

    public HomeAssistant(@Value("${homeassistant.api.url}") String homeAssistantUrl,
                         @Value("${homeassistant.api.token}") String homeAssistantToken) {
        this.homeAssistantUrl = homeAssistantUrl;
        this.homeAssistantToken = homeAssistantToken;
    }

    public void setSwitch(String entity, boolean state) {

    }

    private void callApi(String query, String path, String requestMethod) {
        try {
            URL url = new URL(homeAssistantUrl + path);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod(requestMethod);
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Authorization", "Bearer " + homeAssistantToken);
            con.setDoOutput(true);

            try (OutputStream os = con.getOutputStream()) {
                byte[] input = query.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = con.getResponseCode();
            logger.info("Response Code: {}", responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {

            } else {
                logger.error("POST request failed with code: {}", responseCode);
            }
        } catch (Exception e) {
            logger.error("API call failed: {}", e.getMessage(), e);
        }
    }

}
