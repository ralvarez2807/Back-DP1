package com.tasf.b2b.domain.simulator.dto;

import java.time.Instant;

public record FlightArrivedDTO(
        Instant simTime,
        String  flightId,
        String  toIcao,
        int     load
) implements StateChangeDTO {}
