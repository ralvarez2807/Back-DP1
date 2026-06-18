package com.tasf.b2b.application.dto;

import java.time.Instant;
import java.util.List;

public record FlightView(
        String  flightId,
        String  fromIcao,
        String  toIcao,
        Instant depTime,
        Instant arrTime,
        String  status,
        int     load,
        int     capacity,
        double  occupancyPct,
        String  occupancyLevel) {

    // EMPTY = 0 %, GREEN ≤ 60 %, AMBER ≤ 85 %, RED > 85 %
    public static String calcLevel(int load, int capacity) {
        if (capacity == 0 || load == 0) return "EMPTY";
        double pct = (double) load / capacity * 100;
        if (pct <= 60) return "GREEN";
        if (pct <= 85) return "AMBER";
        return "RED";
    }

    public static double calcPct(int load, int capacity) {
        if (capacity == 0) return 0.0;
        return Math.round((double) load / capacity * 1000.0) / 10.0;
    }

    public record BaggageOnFlight(
            String  baggageId,
            String  destIcao,
            Instant deadlineUtc) {}

    public record ShipmentOnFlight(
            String                  shipmentId,
            String                  originIcao,
            String                  destIcao,
            int                     baggageCount,
            List<BaggageOnFlight>   baggages) {}

    public record DetailView(
            String                  flightId,
            String                  fromIcao,
            String                  toIcao,
            Instant                 depTime,
            Instant                 arrTime,
            String                  status,
            int                     load,
            int                     capacity,
            double                  occupancyPct,
            String                  occupancyLevel,
            List<ShipmentOnFlight>  shipments) {}
}
