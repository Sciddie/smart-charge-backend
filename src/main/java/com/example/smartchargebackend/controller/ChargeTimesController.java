package com.example.smartchargebackend.controller;

import com.example.smartchargebackend.records.PriceData;
import com.example.smartchargebackend.service.TibberAPI;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/charge-times")
public class ChargeTimesController {

    // Get charge times for a given device ID
    @GetMapping
    public List<PriceData> getChargeTimes(@RequestParam String id) {
        return TibberAPI.getChargingHours(id);
    }

    // Schedule charge times with optional start time and timeframe validation
    @PostMapping("/schedule")
    public List<PriceData> scheduleChargeTimes(@RequestParam String id,
                                               @RequestParam(required = false) OffsetDateTime from,
                                               @RequestParam(required = false) Integer timeframe,
                                               @RequestParam Integer hours) {
        // Case 1: Only `id` and `hours` provided
        if (from == null && timeframe == null) {
            return TibberAPI.scheduleChargingHoursForId(id, hours);
        }

        // Case 2: `id`, `timeframe`, and `hours` provided (without `from`)
        if (from == null) {
            return TibberAPI.scheduleChargingHoursForId(id, timeframe, hours);
        }

        // Case 3: `from` is provided but `timeframe` is missing (invalid case)
        if (timeframe == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing timeframe. 'from' requires a valid timeframe.");
        }

        // Case 4: All parameters are provided (`id`, `from`, `timeframe`, and `hours`)
        return TibberAPI.scheduleChargingHoursForId(id, from, timeframe, hours);
    }

    // Get list of devices
    @GetMapping("/devices")
    public List<String> getDevices() {
        return TibberAPI.getDevices();
    }

    // Remove a device by ID
    @DeleteMapping("/devices/{id}")
    public void removeDevice(@PathVariable String id) {
        TibberAPI.removeDevice(id);
    }

    // Get all price data
    @GetMapping("/prices")
    public List<PriceData> getPrices() {
        return TibberAPI.getPriceList();
    }

    // Get the cheapest hours for a given number of hours
    @GetMapping("/prices/cheapest")
    public List<PriceData> getCheapestHours(@RequestParam Integer hours) {
        return TibberAPI.getCheapestHours(hours);
    }
}
