package com.tasf.b2b.domain.optimizer;

import com.tasf.b2b.domain.model.graph.projection.GraphProjection;

/**
 * Contrato de todos los optimizadores de ruteo.
 * Implementado por ALNSAlgorithm, GeneticAlgorithm, etc.
 * El hilo optimizador construye un snapshot (GraphProjection) y lo pasa aquí;
 * el algoritmo no accede al grafo en vivo.
 */
public interface RoutingOptimizer {
    SolutionResult optimize(GraphProjection projection);
}
