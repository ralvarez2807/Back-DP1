package com.tasf.b2b.application.dto;

import java.time.Instant;
import java.util.List;

/**
 * Resultado final de una sesión que ya terminó (fin normal, colapso o stop manual):
 * su estado y la última solución asignada (ruta de cada maleta).
 */
public record FinishedSessionView(
        String                  id,
        String                  username,
        String                  status,          // COMPLETED | COLLAPSED | STOPPED
        Instant                 endedAt,
        String                  collapseReason,  // solo si status = COLLAPSED
        List<BaggageRouteView>  assignedRoutes) {}
