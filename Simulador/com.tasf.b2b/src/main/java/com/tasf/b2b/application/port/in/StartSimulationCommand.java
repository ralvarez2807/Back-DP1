package com.tasf.b2b.application.port.in;

import com.tasf.b2b.domain.simulator.SimulationConfig;
import java.time.Instant;

/**
 * DB:     simStart, simEnd, speedFactor requeridos. Una sesión activa por usuario.
 * MANUAL: simStart, simEnd, speedFactor son null (corre a 1x sin fin). Solo una sesión activa.
 */
public record StartSimulationCommand(
        String                            username,
        SimulationConfig.DataSource       dataSource,
        SimulationConfig.SolverTimingMode solverTimingMode,
        SimulationConfig.OptimizerMode    optimizerMode,
        Instant                           simStart,
        Instant                           simEnd,
        Double                            speedFactor
) {}
