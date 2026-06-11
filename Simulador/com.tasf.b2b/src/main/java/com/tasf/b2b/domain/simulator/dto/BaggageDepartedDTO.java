package com.tasf.b2b.domain.simulator.dto;

import java.time.Instant;

public record BaggageDepartedDTO(
        Instant simTime,
        String  baggageId,
        String  flightId,
        String  fromIcao,
        String  toIcao
) implements StateChangeDTO {}
