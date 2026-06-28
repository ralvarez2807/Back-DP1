package com.tasf.b2b.domain.simulator.dto;

import com.tasf.b2b.domain.simulator.CollapseDetector;

import java.time.Instant;

/** El planificador no pudo asignar una maleta a tiempo o en absoluto. */
public record CollapseDetectedDTO(
        Instant simTime,
        CollapseDetector.CollapseReason reason,
        String  baggageId,
        Instant deadline,
        int     consecutiveCycles
) implements StateChangeDTO {}
