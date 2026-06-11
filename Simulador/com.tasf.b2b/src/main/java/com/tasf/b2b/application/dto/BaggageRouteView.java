package com.tasf.b2b.application.dto;

import java.time.Instant;
import java.util.List;

/**
 * Ruta completa de una maleta: tramos ya recorridos (escalas anteriores),
 * el tramo en curso y los tramos planificados. Permite que el frontend dibuje
 * en el mapa la ruta que sigue un producto (maleta) según su ID, mostrando los
 * datos relevantes de sus escalas.
 *
 * state de cada tramo:
 *   TRAVELED  — ya volado (escala anterior confirmada)
 *   IN_FLIGHT — tramo en curso ahora mismo
 *   PLANNED   — tramo futuro planificado por el optimizador
 */
public record BaggageRouteView(
        String     baggageId,
        String     status,        // PENDING | WAITING | IN_FLIGHT | DELIVERED
        String     currentIcao,
        String     destIcao,
        Instant    deadlineUtc,
        List<Leg>  legs) {

    public record Leg(
            String  fromIcao,
            String  toIcao,
            Instant depTime,
            Instant arrTime,
            String  flightId,
            String  state) {}     // TRAVELED | IN_FLIGHT | PLANNED
}
