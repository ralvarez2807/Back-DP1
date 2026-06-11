package com.tasf.b2b.domain.simulator.dto;

import java.time.Instant;

public record FlightCancelledDTO(
        Instant simTime,
        String  flightScheduleKey,
        Instant depTimeUtc
) implements StateChangeDTO {}
