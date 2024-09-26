package com.example.smartchargebackend.records;


import java.time.OffsetDateTime;

public record PriceData(double total, OffsetDateTime startsAt) {

    @Override
    public String toString() {
        return "Total: " + total + ", Starts At: " + startsAt;
    }
}
