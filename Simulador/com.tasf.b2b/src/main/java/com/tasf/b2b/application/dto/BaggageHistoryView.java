package com.tasf.b2b.application.dto;

import java.time.Instant;
import java.util.List;

/**
 * Historial de transiciones de estado de una maleta, con timestamp (LE-45).
 * Distinto de {@link BaggageRouteView}: aquí se registra cada cambio de estado
 * (PENDING → WAITING → IN_FLIGHT → ... → DELIVERED), no solo los tramos de vuelo.
 */
public record BaggageHistoryView(String baggageId, List<Entry> entries) {

    public record Entry(
            Instant timestamp,
            String  status,     // PENDING | WAITING | IN_FLIGHT | DELIVERED
            String  icao,       // aeropuerto donde ocurrió la transición (o de salida si IN_FLIGHT)
            String  flightId) { // solo si status = IN_FLIGHT
    }
}
