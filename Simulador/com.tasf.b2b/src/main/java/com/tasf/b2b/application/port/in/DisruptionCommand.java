package com.tasf.b2b.application.port.in;

import java.time.Instant;

/**
 * Orden de inyección de una circunstancia (disrupción) sobre una sesión activa.
 *
 * Todas las disrupciones se materializan cancelando los vuelos afectados en el
 * motor de simulación (vía FlightCancelledEvent), de modo que el efecto es real:
 * las maletas afectadas se re-enrutan. El tipo y la severidad son metadatos que el
 * frontend usa para representar cada circunstancia con su color e ideografía propia.
 *
 *   CANCELLATION  — cancela un vuelo concreto (flightId).
 *   AVERIA        — avería de una UT (flightId) con severity 1..4 (Leve..Desastre);
 *                   cancela el/los vuelo(s) de esa UT.
 *   SEGMENT_BLOCK — bloqueo de tramo origen→destino en la ventana [fromUtc, toUtc].
 *   NODE_BLOCK    — bloqueo de nodo (aeropuerto) en la ventana [fromUtc, toUtc].
 */
public record DisruptionCommand(
        Kind    kind,
        String  flightId,      // CANCELLATION / AVERIA
        String  originIcao,    // SEGMENT_BLOCK / NODE_BLOCK
        String  destIcao,      // SEGMENT_BLOCK
        Instant fromUtc,       // ventana de bloqueo (nullable)
        Instant toUtc,         // ventana de bloqueo (nullable)
        int     severity) {    // AVERIA 1..4 (0 si no aplica)

    public enum Kind { CANCELLATION, AVERIA, SEGMENT_BLOCK, NODE_BLOCK }
}
