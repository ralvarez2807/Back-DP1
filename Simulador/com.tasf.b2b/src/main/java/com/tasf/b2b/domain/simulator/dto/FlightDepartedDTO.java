package com.tasf.b2b.domain.simulator.dto;

import java.time.Instant;

public record FlightDepartedDTO(
        Instant simTime,
        String  flightId,
        String  fromIcao,
        String  toIcao,
        int     load,
        int     capacity
) implements StateChangeDTO {}
