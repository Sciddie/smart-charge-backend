package com.example.smartchargebackend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class HomeAssistant {
    private final String homeAssistantUrl;
    private final String homeAssistantToken;

    public HomeAssistant(@Value("${homeassistant.api.url}") String homeAssistantUrl,
                         @Value("${homeassistant.api.token}") String homeAssistantToken) {
        this.homeAssistantUrl = homeAssistantUrl;
        this.homeAssistantToken = homeAssistantToken;
    }
}
