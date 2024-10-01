package com.example.smartchargebackend.controller;

import com.example.smartchargebackend.records.PriceData;
import com.example.smartchargebackend.service.TibberAPI;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ChargeTimesController {
    @GetMapping("/getchargetimes")
    public List<PriceData> getChargeTimes(@RequestParam String id) {
        return TibberAPI.getChargingHours(id);
    }
    @GetMapping("/setchargetimestf")
    public List<PriceData> setChargeTimes(@RequestParam String id, @RequestParam Integer timeframe,
                                          @RequestParam Integer hours) {
        return TibberAPI.scheduleChargingHoursForId(id, timeframe, hours);
    }
    @GetMapping("/setchargetimes")
    public List<PriceData> setChargeTimes(@RequestParam String id,
                                          @RequestParam Integer hours) {
        return TibberAPI.scheduleChargingHoursForId(id, hours);
    }
    @GetMapping("/devices")
    public List<String> getDevices() {
        return TibberAPI.getDevices();
    }
    @GetMapping("/remove")
    public void remove(@RequestParam String id) {
        TibberAPI.removeDevice(id);
    }
}
