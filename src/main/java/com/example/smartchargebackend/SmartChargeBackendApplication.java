package com.example.smartchargebackend;

import com.example.smartchargebackend.service.TibberAPI;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SmartChargeBackendApplication {

    public static void main(String[] args) {
        TibberAPI.scheduleChargingHoursForId("car", 5);
        TibberAPI.scheduleChargingHoursForId("smartphone", 8);
        System.out.println(TibberAPI.getChargingHours("car"));
        System.out.println(TibberAPI.getChargingHours("smartphone"));
        SpringApplication.run(SmartChargeBackendApplication.class, args);
    }

}
