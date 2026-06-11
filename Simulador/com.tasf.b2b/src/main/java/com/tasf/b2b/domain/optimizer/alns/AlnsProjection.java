package com.tasf.b2b.domain.optimizer.alns;

import com.tasf.b2b.domain.model.graph.componentsgraph.FlightEdge;
import com.tasf.b2b.domain.model.graph.movable.Baggage;
import com.tasf.b2b.domain.model.graph.projection.GraphProjection;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

/**
 * Snapshot inmutable del grafo para el ALNS.
 * El algoritmo solo lee BaggageState y FlightSnapshot — nunca objetos vivos.
 * baggageById y flightById son referencias vivas usadas únicamente en toSolutionResult().
 */
public record AlnsProjection(
        List<BaggageState>                                        pendingBaggages,
        Map<String, NavigableMap<Instant, List<FlightSnapshot>>>  flightsByOrigin,
        int                                                        minConnectionMinutes,
        int                                                        pickupMinutes,
        Instant                                                    snapshotTime,
        Map<String, Baggage>                                       baggageById,
        Map<String, FlightEdge>                                    flightById
) implements GraphProjection {}
