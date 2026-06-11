package com.tasf.b2b.domain.simulator.dto;

import java.time.Instant;

/**
 * Tipo base sellado para todos los deltas de estado que el runner publica.
 * Cada subtipo es un record autocontenido: lleva el estado real observado,
 * no referencias al plan. El consumidor (Redis, tests) no necesita cruzar
 * datos con mensajes anteriores.
 */
public sealed interface StateChangeDTO
        permits FlightScheduledDTO, FlightDepartedDTO, BaggageDepartedDTO,
                FlightArrivedDTO, BaggageArrivedDTO, BaggageDeliveredDTO,
                BaggagePendingDTO, FlightCancelledDTO, ShipmentCreatedDTO,
                BaggageAssignedDTO {

    Instant simTime();
}
