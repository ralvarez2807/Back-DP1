package com.tasf.b2b.application.dto;

import java.time.Instant;
import java.util.List;

public record AirportLiveView(
        String  icao,
        String  city,
        String  continent,
        int     load,
        int     capacity,
        double  occupancyPct,
        String  occupancyLevel) {

    public record InboundFlight(
            String       flightId,
            String       fromIcao,
            Instant      arrTime,
            int          baggageCount,
            List<String> shipmentIds) {}

    public record OutboundFlight(
            String       flightId,
            String       toIcao,
            Instant      depTime,
            int          baggageCount,
            List<String> shipmentIds) {}

    public record TransitBaggage(
            String  baggageId,
            String  shipmentId,
            String  destIcao,
            Instant deadlineUtc,
            String  nextFlightId,  // null si PENDING
            Instant nextDepTime) {} // null si PENDING

    public record InboundView(
            String              icao,
            Instant             simTime,
            List<InboundFlight> inbound) {}

    public record OutboundView(
            String               icao,
            Instant              simTime,
            List<OutboundFlight> outbound) {}

    public record TransitView(
            String               icao,
            Instant              simTime,
            List<TransitBaggage> transit) {}
}
