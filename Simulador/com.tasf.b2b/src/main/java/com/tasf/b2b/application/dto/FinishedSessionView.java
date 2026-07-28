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
 *
 * {@code report}: foto de GET /reports/summary tomada en el mismo instante en
 * que se arma este registro (ver RunSimulationUseCase, fin natural/colapso y
 * stop()) — mientras la sesión todavía está en el SimulationRegistry, así
 * QuerySimulationUseCase.getReport() puede devolverla como respaldo una vez
 * que el registry ya evictó la sesión (15s) pero este caché todavía no expiró
 * (5 min). Null en el snapshot periódico "RUNNING" (no es un cierre, y
 * mientras corre getReport() igual puede calcularlo en vivo desde el registry).
 */
public record FinishedSessionView(
        String                  id,
        String                  username,
        String                  status,          // RUNNING | COMPLETED | COLLAPSED | STOPPED
        Instant                 endedAt,         // instante de este registro (no necesariamente el fin real)
        String                  collapseReason,  // solo si status = COLLAPSED
        List<BaggageRouteView>  assignedRoutes,  // última planificación EXITOSA (nunca la que colapsó)
        ReportView              report) {}       // null salvo en el registro terminal (COMPLETED/COLLAPSED/STOPPED)
