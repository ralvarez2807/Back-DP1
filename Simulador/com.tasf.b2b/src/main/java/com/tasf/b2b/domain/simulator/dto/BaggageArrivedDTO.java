package com.tasf.b2b.domain.simulator.dto;

import java.time.Instant;

/** Maleta llegó a un aeropuerto intermedio y sigue en ruta. */
public record BaggageArrivedDTO(
        Instant simTime,
        String  baggageId,
        String  flightId,
        String  currentIcao
) implements StateChangeDTO {}
