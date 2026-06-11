package com.tasf.b2b.domain.optimizer.alns;

import java.time.Instant;

/**
 * Snapshot inmutable de un FlightEdge en el momento del snapshot.
 * remainingCapacity captura fe.getRemainingCapacity() en ese instante.
 */
public record FlightSnapshot(
        String  flightId,
        String  fromIcao,
        String  toIcao,
        Instant depTime,
        Instant arrTime,
        int     remainingCapacity
) {}
