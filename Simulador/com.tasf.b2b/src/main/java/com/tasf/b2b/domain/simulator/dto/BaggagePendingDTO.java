package com.tasf.b2b.domain.simulator.dto;

import java.time.Instant;

/** Maleta sin ruta asignada — necesita ser re-enrutada. */
public record BaggagePendingDTO(
        Instant simTime,
        String  baggageId,
        String  currentIcao
) implements StateChangeDTO {}
