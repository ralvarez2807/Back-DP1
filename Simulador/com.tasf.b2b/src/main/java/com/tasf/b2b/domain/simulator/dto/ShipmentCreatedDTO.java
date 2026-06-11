package com.tasf.b2b.domain.simulator.dto;

import java.time.Instant;
import java.util.List;

public record ShipmentCreatedDTO(
        Instant      simTime,
        String       shipmentId,
        List<String> baggageIds,
        String       originIcao,
        String       destIcao,
        Instant      deadline
) implements StateChangeDTO {}
