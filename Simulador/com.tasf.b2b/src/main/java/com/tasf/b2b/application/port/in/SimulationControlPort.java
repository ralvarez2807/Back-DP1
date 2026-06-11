package com.tasf.b2b.application.port.in;

import java.time.Instant;
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
     * @param simStart inicio del periodo a simular (UTC)
     * @param simEnd   fin del periodo a simular (UTC)
     * @return UUID de sesión — usar en todas las llamadas posteriores
     */
    String start(Instant simStart, Instant simEnd);

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
