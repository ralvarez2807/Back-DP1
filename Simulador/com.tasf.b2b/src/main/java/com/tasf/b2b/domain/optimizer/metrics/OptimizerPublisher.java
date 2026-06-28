package com.tasf.b2b.domain.optimizer.metrics;

public interface OptimizerPublisher {
    void publish(OptimizerEventDTO event);
    default void close() {}
}
