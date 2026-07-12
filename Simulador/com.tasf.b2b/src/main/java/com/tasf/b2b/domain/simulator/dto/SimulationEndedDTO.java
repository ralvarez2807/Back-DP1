package com.tasf.b2b.domain.simulator.dto;

import java.time.Instant;

/** La simulación terminó: fin normal, colapso, o detenida manualmente. Cierra el WS. */
public record SimulationEndedDTO(
        Instant simTime,
        String  status,          // COMPLETED | COLLAPSED | STOPPED
        String  collapseReason   // solo si status = COLLAPSED
) implements StateChangeDTO {}
