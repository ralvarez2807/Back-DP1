package com.tasf.b2b.domain.optimizer.metrics;

import com.tasf.b2b.domain.simulator.CollapseDetector;

import java.time.Instant;
import java.util.List;

/** Detalle completo del colapso: qué maletas no pudieron ser asignadas y por qué. */
public record CollapseDetailDTO(
        Instant                   simTime,
        CollapseDetector.CollapseReason reason,
        int                       consecutiveCycles,
        List<UnroutedBaggageInfo> unroutedBaggages
) implements OptimizerEventDTO {

    /**
     * Detalle de una maleta sin solución en el momento del colapso.
     * minutesOverDeadline > 0 si ya superó su deadline; 0 si aún no lo superó
     * pero el optimizador no encontró ninguna ruta viable.
     */
    public record UnroutedBaggageInfo(
            String  baggageId,
            String  originIcao,
            String  destIcao,
            Instant availableFrom,
            Instant deadline,
            long    minutesOverDeadline
    ) {}
}
