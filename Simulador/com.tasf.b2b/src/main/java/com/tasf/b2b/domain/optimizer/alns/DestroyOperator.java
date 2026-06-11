package com.tasf.b2b.domain.optimizer.alns;

public interface DestroyOperator {
    /** Elimina hasta k baggages de la solución y los mueve a unrouted. */
    void destroy(BaggageSolution solution, AlnsProjection projection, int k);
    String name();
}
