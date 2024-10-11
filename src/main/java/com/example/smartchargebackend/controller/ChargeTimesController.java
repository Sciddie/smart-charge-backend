package com.example.smartchargebackend.controller;

import com.example.smartchargebackend.exception.ResourceNotFoundException;
import com.example.smartchargebackend.records.PriceData;
import com.example.smartchargebackend.service.TibberAPI;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/charge-times")
@Tag(name = "Charge Times", description = "API for managing device charge times")
public class ChargeTimesController {

    private final TibberAPI tibberAPI;

    public ChargeTimesController(TibberAPI tibberAPI) {
        this.tibberAPI = tibberAPI;
    }

    @GetMapping
    @Operation(summary = "Get charge times for a device", description = "Retrieve charge times for a given device ID")
    public List<PriceData> getChargeTimes(@Parameter(description = "Device ID") @RequestParam String id) {
        List<PriceData> chargeTimes = tibberAPI.getChargingHours(id);
        if (chargeTimes == null || chargeTimes.isEmpty()) {
            throw new ResourceNotFoundException("No charge times found for device with ID: " + id);
        }
        return chargeTimes;
    }

    @PostMapping("/schedule")
    @Operation(summary = "Schedule charge times", description = "Schedule charge times for a device with optional parameters")
    public List<PriceData> scheduleChargeTimes(
            @Parameter(description = "Device ID") @RequestParam String id,
            @Parameter(description = "Start time") @RequestParam(required = false) OffsetDateTime from,
            @Parameter(description = "Time frame in hours") @RequestParam(required = false) Integer timeframe,
            @Parameter(description = "Number of hours to charge") @RequestParam Integer hours) {

        if (from == null && timeframe == null) {
            return tibberAPI.scheduleChargingHoursForId(id, hours);
        }

        if (from == null) {
            return tibberAPI.scheduleChargingHoursForId(id, timeframe, hours);
        }

        if (timeframe == null) {
            throw new IllegalArgumentException("Missing timeframe. 'from' requires a valid timeframe.");
        }

        return tibberAPI.scheduleChargingHoursForId(id, from, timeframe, hours);
    }

    @GetMapping("/devices")
    @Operation(summary = "Get list of devices", description = "Retrieve a list of all device IDs")
    public List<String> getDevices() {
        return tibberAPI.getDevices();
    }

    @DeleteMapping("/devices/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a device", description = "Remove a device by its ID")
    public void removeDevice(@Parameter(description = "Device ID") @PathVariable String id) {
        tibberAPI.removeDevice(id);
    }

    @GetMapping("/prices")
    @Operation(summary = "Get all price data", description = "Retrieve all available price data")
    public List<PriceData> getPrices() {
        return tibberAPI.getPriceList();
    }

    @GetMapping("/prices/cheapest")
    @Operation(summary = "Get cheapest hours", description = "Retrieve the cheapest hours for a given number of hours")
    public List<PriceData> getCheapestHours(@Parameter(description = "Number of hours") @RequestParam Integer hours) {
        return tibberAPI.getCheapestHours(hours);
    }
}