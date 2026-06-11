package com.tasf.b2b.domain.optimizer.alns;

import java.time.Instant;

/**
 * Snapshot inmutable de un baggage pendiente. El ALNS trabaja exclusivamente
 * con este record — nunca accede al Baggage vivo del grafo.
 */
public record BaggageState(
        String  baggageId,
        String  currentIcao,
        Instant availableFrom,   // más tarde entre el tiempo del nodo actual y snapshotTime
        String  destIcao,
        Instant deadline
) {}
