package com.tasf.b2b.domain.simulator.dto;

import java.time.Instant;
import java.util.List;

public record BaggageAssignedDTO(
        Instant      simTime,
        String       baggageId,
        List<String> route
) implements StateChangeDTO {}
