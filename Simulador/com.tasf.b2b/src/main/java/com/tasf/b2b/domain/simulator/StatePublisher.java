package com.tasf.b2b.domain.simulator;

import com.tasf.b2b.domain.simulator.dto.StateChangeDTO;

/**
 * Puerto requerido: el runner llama publish() tras cada handler.
 * La implementación concreta (Redis, test spy, no-op) vive en infrastructure.
 * close() se llama cuando la sesión termina; la implementación Redis lo usa
 * para interrumpir su hilo drenador.
 */
public interface StatePublisher extends AutoCloseable {
    void publish(StateChangeDTO dto);

    @Override
    default void close() {}
}
