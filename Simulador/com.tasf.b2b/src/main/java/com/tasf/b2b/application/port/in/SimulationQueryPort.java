package com.tasf.b2b.application.port.in;

import com.tasf.b2b.application.dto.BaggageRouteView;
import com.tasf.b2b.application.dto.BaggageView;
import com.tasf.b2b.application.dto.DashboardView;
import com.tasf.b2b.application.dto.SimSessionView;
import com.tasf.b2b.application.dto.SnapshotView;

/**
 * Puerto de entrada para consultas de estado de una sesión de simulación.
 * Todos los métodos leen en tiempo real del SpaceTimeGraph en memoria.
 */
public interface SimulationQueryPort {

    /**
     * Estado actual de la sesión (tiempo simulado, status, rango de fechas).
     *
     * @throws IllegalArgumentException si la sesión no existe
     */
    SimSessionView getSession(String sessionId);

    /**
     * Métricas agregadas calculadas en el momento de la llamada.
     * Leer frecuentemente para polling del dashboard.
     *
     * @throws IllegalArgumentException si la sesión no existe
     */
    DashboardView getDashboard(String sessionId);

    /**
     * Estado de tracking de una maleta individual.
     *
     * @throws IllegalArgumentException si la sesión o la maleta no existen
     */
    BaggageView getBaggageState(String sessionId, String baggageId);

    /**
     * Snapshot completo del estado actual: todos los vuelos y maletas en el horizonte.
     * El frontend lo llama una vez al conectar el WebSocket para obtener el estado
     * inicial y luego aplica los eventos incrementales encima.
     *
     * @throws IllegalArgumentException si la sesión no existe
     */
    SnapshotView getSnapshot(String sessionId);

    /**
     * Ruta completa de una maleta (tramos recorridos + en curso + planificados),
     * para dibujar en el mapa la ruta que sigue según su ID, con sus escalas.
     *
     * @throws IllegalArgumentException si la sesión o la maleta no existen
     */
    BaggageRouteView getBaggageRoute(String sessionId, String baggageId);
}
