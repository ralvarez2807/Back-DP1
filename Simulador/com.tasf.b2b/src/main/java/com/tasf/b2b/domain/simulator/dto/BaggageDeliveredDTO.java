package com.tasf.b2b.domain.simulator.dto;

import java.time.Instant;

/** Maleta llegó a su aeropuerto destino. */
public record BaggageDeliveredDTO(
        Instant simTime,
        String  baggageId,
        String  currentIcao
) implements StateChangeDTO {}
