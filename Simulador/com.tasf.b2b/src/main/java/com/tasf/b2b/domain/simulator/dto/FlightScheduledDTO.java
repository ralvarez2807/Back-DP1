package com.tasf.b2b.domain.simulator.dto;

import java.time.Instant;

public record FlightScheduledDTO(
        Instant simTime,
        String  flightId,
        String  fromIcao,
        String  toIcao,
        Instant depTime,
        int     capacity
) implements StateChangeDTO {}
