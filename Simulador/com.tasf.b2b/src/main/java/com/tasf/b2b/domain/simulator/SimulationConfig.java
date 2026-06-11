package com.tasf.b2b.domain.simulator;

import java.time.Instant;

public record SimulationConfig(
        SolverTimingMode solverTimingMode,
        OptimizerMode    optimizerMode,
        double           speedFactor,
        Instant          simStart,
        Instant          simEnd,
        DataSource       dataSource,
        int              minConnectionMinutes,
        int              pickupMinutes
) {
    public enum SolverTimingMode { REAL_TIME, PAUSE, EVENT_DRIVEN }

    public enum OptimizerMode {
        ALNS_ONLY,
        GENETIC_ONLY,
        ALNS_ACTIVE_GENETIC_EVAL,
        GENETIC_ACTIVE_ALNS_EVAL
    }

    public enum DataSource { TXT, MANUAL }

    public static SimulationConfig defaultTxt(Instant simStart, Instant simEnd, double speedFactor) {
        return new SimulationConfig(
                SolverTimingMode.REAL_TIME,
                OptimizerMode.ALNS_ONLY,
                speedFactor,
                simStart,
                simEnd,
                DataSource.TXT,
                10,
                10
        );
    }
}
