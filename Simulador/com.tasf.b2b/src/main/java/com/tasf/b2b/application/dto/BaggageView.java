package com.tasf.b2b.application.dto;

import java.time.Instant;

/**
 * Estado actual de una maleta individual devuelto por tracking.
 *
 * status       — PENDING | WAITING | IN_FLIGHT | DELIVERED
 * currentIcao  — aeropuerto donde está ahora; si IN_FLIGHT, es el aeropuerto de salida del tramo en curso
 * flightId     — ID del vuelo que lleva la maleta; null si no está en vuelo
 * destIcao     — destino final de la maleta
 * deadlineUtc  — límite de entrega calculado al crear el shipment
 */
public record BaggageView(
        String  baggageId,
        String  status,
        String  currentIcao,
        String  flightId,    // null salvo IN_FLIGHT
        String  destIcao,
        Instant deadlineUtc
) {}
