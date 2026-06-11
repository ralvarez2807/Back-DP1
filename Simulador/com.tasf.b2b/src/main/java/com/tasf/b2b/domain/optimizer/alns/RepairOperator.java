package com.tasf.b2b.domain.optimizer.alns;

public interface RepairOperator {
    /** Intenta enrutar todos los baggages en solution.unrouted(). */
    void repair(BaggageSolution solution, AlnsProjection projection);
    String name();
}
