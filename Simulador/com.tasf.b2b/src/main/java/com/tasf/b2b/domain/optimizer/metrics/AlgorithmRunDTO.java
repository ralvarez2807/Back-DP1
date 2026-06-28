package com.tasf.b2b.domain.optimizer.metrics;

import java.time.Instant;

/** Métricas de una ejecución individual del optimizador. */
public record AlgorithmRunDTO(
        Instant simTime,
        int     runNumber,
        long    executionMs,
        int     routedCount,
        int     unroutedCount,
        double  score,
        double  avgScore
) implements OptimizerEventDTO {}
