package com.example.smartchargebackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SmartChargeBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartChargeBackendApplication.class, args);
    }

}