package com.tasf.b2b.application.dto;

import java.time.Instant;
import java.util.List;

/**
 * Última solución EXITOSA conocida de una sesión: su estado y la ruta de cada
 * maleta según la última planificación buena.
 *
 * Mientras la sesión corre, se sobreescribe periódicamente con status "RUNNING"
 * (ver RunSimulationUseCase) para que, si la ejecución se corta de forma abrupta,
 * la última planificación exitosa siga disponible por API.
 *
 * Al terminar:
 *  - COMPLETED / STOPPED: se sobreescribe con el estado actual de las maletas
 *    (no hubo falla, así que es la última buena de por sí).
 *  - COLLAPSED: assignedRoutes NO se recalcula con el estado que causó el
 *    colapso (deadline vencido / maleta sin ruta) — se conserva el último
 *    snapshot bueno anterior a la falla. Solo status y collapseReason reflejan
 *    el colapso.
 */
public record FinishedSessionView(
        String                  id,
        String                  username,
        String                  status,          // RUNNING | COMPLETED | COLLAPSED | STOPPED
        Instant                 endedAt,         // instante de este registro (no necesariamente el fin real)
        String                  collapseReason,  // solo si status = COLLAPSED
        List<BaggageRouteView>  assignedRoutes) {} // última planificación EXITOSA (nunca la que colapsó)
