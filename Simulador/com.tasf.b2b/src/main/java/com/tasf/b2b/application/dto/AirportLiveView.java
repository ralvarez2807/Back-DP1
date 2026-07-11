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

    public record InboundBaggage(
            String baggageId,
            String shipmentId,
            String destIcao) {}

    public record InboundFlight(
            String                flightId,
            String                fromIcao,
            Instant               arrTime,
            int                   baggageCount,
            List<String>          shipmentIds,
            List<InboundBaggage>  baggages) {}

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
            String  nextFlightId,   // null si PENDING
            Instant nextDepTime,    // null si PENDING
            Instant arrivedAt,      // llegó al almacén (última entrada de historial en este ICAO)
            Instant readyAt,        // fin de la ventana de conexión: arrivedAt + minConnectionMinutes
            boolean awaitingPickup, // true = llegó a su destino final y espera recojo del pasajero
            Instant pickupAt) {}    // instante en que se recogerá (y descontará del almacén)

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
