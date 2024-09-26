package com.example.smartchargebackend.records;

import java.time.OffsetDateTime;
import java.util.ArrayList;

public record ChargeTimes(ArrayList<OffsetDateTime> chargeTimes) {}
