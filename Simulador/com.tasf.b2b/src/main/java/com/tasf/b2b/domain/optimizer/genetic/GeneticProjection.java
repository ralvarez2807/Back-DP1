package com.tasf.b2b.domain.optimizer.genetic;

import com.tasf.b2b.domain.model.graph.componentsgraph.FlightEdge;
import com.tasf.b2b.domain.model.graph.movable.Baggage;
import com.tasf.b2b.domain.model.graph.projection.GraphProjection;

import java.time.Instant;
import java.util.List;

/**
 * Snapshot del grafo para uso exclusivo del Algoritmo Genético.
 * Se construye en GeneticThread antes de optimizar; no se modifica durante el algoritmo.
 *
 * pendingBaggages: copia superficial — el Genético no debe mutarlos.
 * availableFlights: vuelos activos en el horizonte al momento del snapshot.
 * TODO: agregar estructura de población inicial cuando se implemente el algoritmo.
 */
public record GeneticProjection(
        List<Baggage>    pendingBaggages,
        List<FlightEdge> availableFlights,
        Instant          snapshotTime
) implements GraphProjection {}
