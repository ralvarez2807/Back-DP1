package com.tasf.b2b.application.port.in;

import java.util.List;

/**
 * Puerto de entrada para el ciclo de vida de una sesión de simulación.
 *
 * "Puerto de entrada" = interface que la presentación (controladores REST)
 * llama. La implementación vive en application/usecase/.
 *
 * El dominio no conoce esta interface — la capa de aplicación es la
 * intermediaria entre presentación y dominio.
 */
public interface SimulationControlPort {

    /**
     * Crea e inicia una sesión de simulación.
     *
     * @return UUID de sesión — usar en todas las llamadas posteriores
     * @throws UnsupportedOperationException si la combinación de modos no está implementada (→ 501)
     * @throws IllegalStateException         si ya existe una sesión MANUAL activa (→ 409)
     */
    String start(StartSimulationCommand command);

    /**
     * Pausa el reloj de simulación. Los eventos dejan de dispararse;
     * los hilos inyectores y optimizadores quedan bloqueados.
     *
     * @throws IllegalArgumentException si la sesión no existe
     * @throws IllegalStateException    si la sesión no está en estado running
     */
    void pause(String sessionId);

    /**
     * Reanuda el reloj desde donde se pausó.
     *
     * @throws IllegalArgumentException si la sesión no existe
     * @throws IllegalStateException    si la sesión no está en estado paused
     */
    void resume(String sessionId);

    /**
     * Detiene la simulación e interrumpe todos sus hilos.
     * La sesión se elimina del registry — no puede reanudarse.
     *
     * @throws IllegalArgumentException si la sesión no existe
     */
    void stop(String sessionId);

    /**
     * Inyecta una circunstancia (cancelación, avería, bloqueo de tramo o de nodo)
     * sobre una sesión en curso. Cancela los vuelos afectados en el motor de
     * simulación de forma thread-safe (vía eventos), por lo que las maletas se
     * re-enrutan automáticamente.
     *
     * @return IDs de los vuelos (FlightEdge) afectados — puede estar vacía.
     * @throws IllegalArgumentException si la sesión no existe
     */
    List<String> injectDisruption(String sessionId, DisruptionCommand command);
}
