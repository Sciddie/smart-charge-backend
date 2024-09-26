package com.example.smartchargebackend.controller;

import com.example.smartchargebackend.records.ChargeTimes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.ArrayList;

@RestController
public class ChargeTimesController {
    @GetMapping("/chargetimes")
    public ChargeTimes getChargeTimes() {
        ArrayList<OffsetDateTime> chargetimes = new ArrayList<>();
        chargetimes.add(OffsetDateTime.parse("2024-09-11T16:00:00.000+02:00"));
        return new ChargeTimes(chargetimes);
    }
}
