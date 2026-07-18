package com.tasf.b2b.application.dto;

import java.time.Instant;

/**
 * Vista de una sesión de simulación devuelta por la capa de aplicación.
 * Los controladores REST mapean este record a sus propios DTOs de API.
 */
public record SimSessionView(
        String  id,          // UUID de la sesión
        String  status,      // "starting" | "running" | "paused" | "completed" | "stopped"
        Instant simTime,     // tiempo actual dentro de la simulación (no el reloj de pared)
        Instant simStart,
        Instant simEnd,
        double  speedFactor  // sim-horas por hora real: el frontend lo necesita para
                             // animar los aviones al ritmo correcto (ver anim de vuelos)
) {}
