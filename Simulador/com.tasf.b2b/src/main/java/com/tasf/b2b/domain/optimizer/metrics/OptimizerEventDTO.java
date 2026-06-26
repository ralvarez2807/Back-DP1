package com.tasf.b2b.domain.optimizer.metrics;

import java.time.Instant;

public sealed interface OptimizerEventDTO
        permits AlgorithmRunDTO, CollapseDetailDTO {
    Instant simTime();
}
