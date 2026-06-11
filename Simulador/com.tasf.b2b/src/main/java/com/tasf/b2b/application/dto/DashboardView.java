package com.tasf.b2b.application.dto;

import java.time.Instant;

/**
 * Métricas agregadas de una sesión de simulación en tiempo real.
 * Calculadas en el momento de la consulta — no cacheadas.
 *
 * delivered        — baggages que llegaron a su aeropuerto destino
 * pending          — baggages sin ruta asignada (esperando próxima solución ALNS)
 * assigned         — baggages con ruta planificada, esperando en aeropuerto
 * inFlight         — baggages actualmente en el aire (currentEdge es FlightEdge)
 * slaBreaches      — baggages cuyo deadlineUtc ya pasó (en cualquier estado)
 * throughputPerHour — entregados / horas simuladas transcurridas
 */
public record DashboardView(
        Instant simTime,
        int     delivered,
        int     pending,
        int     assigned,
        int     inFlight,
        int     slaBreaches,
        double  throughputPerHour
) {}
