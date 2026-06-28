package com.tasf.b2b.application.dto;

import java.time.Instant;
import java.util.List;

/**
 * Forense de por qué un envío tiene maletas que incumplen el SLA o quedaron sin ruta.
 * Reconstruye el contexto del momento (ubicación, deadline, vuelos disponibles) y
 * emite un veredicto por maleta para distinguir un fallo del planificador de una
 * infactibilidad real de horario.
 */
public record ShipmentDiagnosticsView(
        String                 shipmentId,
        String                 originIcao,
        String                 destIcao,
        Instant                deadlineUtc,
        Instant                simNowUtc,
        List<BaggageDiagnostic> baggages) {

    public record BaggageDiagnostic(
            String  baggageId,
            String  status,                 // PENDING | WAITING | IN_FLIGHT | DELIVERED
            String  currentIcao,            // dónde está / a dónde llega
            Instant availableFromUtc,       // desde cuándo puede tomar el primer vuelo
            long    minutesToDeadline,      // negativo si el deadline ya pasó
            boolean hasCompleteRoute,       // tiene plan que llega al destino
            boolean reachableInTime,        // existe ruta (best-effort) que llega a tiempo
            Instant bestEffortArrivalUtc,   // llegada más temprana posible (null si inalcanzable)
            long    bestEffortLateMinutes,  // minutos de retraso de esa mejor ruta (>0 = tarde)
            int     bestEffortHops,         // nº de vuelos de esa mejor ruta
            String  verdict,                // ver VERDICT_*
            String  explanation,            // texto legible para el operario
            List<DirectFlightInfo> directFlights) {}

    /** Vuelos directos origen-actual → destino en el horizonte y por qué sirven o no. */
    public record DirectFlightInfo(
            String  flightId,
            Instant depUtc,
            Instant arrUtc,
            int     remainingCapacity,
            boolean usable,
            String  reason) {}

    // Veredictos
    public static final String VERDICT_DELIVERED_LATE     = "DELIVERED_LATE";      // ya entregada, pero tarde
    public static final String VERDICT_NO_CONNECTIVITY    = "NO_CONNECTIVITY";     // ni ignorando deadline hay ruta
    public static final String VERDICT_DEADLINE_INFEASIBLE = "DEADLINE_INFEASIBLE"; // física: lo más rápido llega tarde
    public static final String VERDICT_PLANNER_MISS       = "PLANNER_MISS";        // existía ruta a tiempo y no se usó
    public static final String VERDICT_ON_TRACK           = "ON_TRACK";            // tiene ruta a tiempo, aún no vencida
}
