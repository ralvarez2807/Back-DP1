package com.tasf.b2b.application.dto;

import java.time.Instant;
import java.util.List;

public record ReportView(
        Instant        simStart,
        Instant        simEnd,
        String         status,
        int            totalShipments,
        int            totalBaggages,
        int            delivered,
        int            slaBreaches,
        int            pending,
        int            inFlight,
        double         throughputPerHour,
        List<TopRoute> topRoutes) {

    public record TopRoute(String fromIcao, String toIcao, int count) {}
}
