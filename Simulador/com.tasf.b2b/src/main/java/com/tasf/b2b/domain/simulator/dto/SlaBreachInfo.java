package com.tasf.b2b.domain.simulator.dto;

import java.time.Instant;
import java.util.List;

/**
 * Foto forense del instante exacto en que una maleta venció su SLA (cruzó su
 * deadline sin haber sido entregada). Capturada por SimulationRunner en el evento
 * de deadline — refleja el estado real del momento, no un cálculo posterior.
 */
public record SlaBreachInfo(
        Instant breachTimeUtc,        // = deadline: el momento en que el contador sube
        String  baggageId,
        String  shipmentId,
        String  originIcao,
        String  destIcao,
        Instant deadlineUtc,
        String  statusAtBreach,       // PENDING | WAITING | IN_FLIGHT
        String  locationIcao,         // dónde estaba físicamente
        boolean hadCompleteRoute,     // tenía un plan que llegaba al destino
        Instant plannedEtaUtc,        // ETA de ese plan (null si no tenía ruta completa)
        long    plannedEtaLateMinutes,// minutos en que ese plan superaba el deadline
        String  cause,                // explicación legible del culpable
        List<Leg> plannedRoute) {

    public record Leg(
            String  fromIcao,
            String  toIcao,
            Instant depUtc,
            Instant arrUtc,
            String  state) {}         // ARRIVED | DEPARTED | PLANNED
}
