package com.tasf.b2b.application.dto;

import java.time.Instant;
import java.util.List;

public record SnapshotView(
        Instant           simTime,
        Instant           simStart,
        Instant           simEnd,
        String            status,
        double            speedFactor,   // segundos simulados por segundo real
        List<FlightSnap>  flights,
        List<BaggageSnap> baggages,
        List<AirportSnap> airports) {

    public record FlightSnap(
            String  flightId,
            String  fromIcao,
            String  toIcao,
            Instant depTime,
            Instant arrTime,
            String  status,   // SCHEDULED | DEPARTED | ARRIVED | CANCELLED
            int     load,
            int     capacity) {}

    public record BaggageSnap(
            String  baggageId,
            String  status,   // PENDING | WAITING | IN_FLIGHT | DELIVERED
            String  currentIcao,
            String  flightId, // null salvo IN_FLIGHT
            String  destIcao,
            Instant deadlineUtc) {}

    /**
     * Ocupación actual de un almacén (aeropuerto), calculada en el backend.
     * load    — maletas físicamente en el almacén ahora (WAITING + PENDING en ese ICAO).
     * pending — subconjunto de load sin ruta asignada (PENDING) — re-enrutamiento.
     * capacity — capacidad fija del almacén (dato inmutable del aeropuerto).
     */
    public record AirportSnap(
            String  icao,
            String  city,
            String  continent,
            int     load,
            int     pending,
            int     capacity) {}
}
