package com.example.smartchargebackend.controller;

import com.example.smartchargebackend.service.HomeAssistant;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@RequestMapping("/api/v1/home-assistant")
@Tag(name = "Home Assistant", description = "API for fetching home assistant devices")
public class HomeAssistantController {
    private final HomeAssistant homeAssistant;

    public HomeAssistantController(HomeAssistant homeAssistant) {
        this.homeAssistant = homeAssistant;
    }

    @GetMapping("/swich-entities")
    public ArrayNode getSwitchEntities() {
        return homeAssistant.getSwitchEntities();
    }
}
