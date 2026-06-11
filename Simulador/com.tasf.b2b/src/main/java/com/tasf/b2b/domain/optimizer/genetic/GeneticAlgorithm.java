package com.tasf.b2b.domain.optimizer.genetic;

import com.tasf.b2b.domain.model.graph.projection.GraphProjection;
import com.tasf.b2b.domain.optimizer.RoutingOptimizer;
import com.tasf.b2b.domain.optimizer.SolutionResult;

/**
 * Implementación del Algoritmo Genético.
 * TODO: castear projection a GeneticProjection e implementar ciclo de
 *       selección, crossover, mutación y reemplazo de población.
 */
public class GeneticAlgorithm implements RoutingOptimizer {

    @Override
    public SolutionResult optimize(GraphProjection projection) {
        return SolutionResult.empty();
    }
}
