package com.tasf.b2b.application.port.in;

import com.tasf.b2b.application.dto.BaggageHistoryView;
import com.tasf.b2b.application.dto.BaggageRouteView;
import com.tasf.b2b.application.dto.BaggageView;
import com.tasf.b2b.application.dto.DashboardView;
import com.tasf.b2b.application.dto.AirportLiveView;
import com.tasf.b2b.application.dto.FlightView;
import com.tasf.b2b.application.dto.ReportView;
import com.tasf.b2b.application.dto.ShipmentDiagnosticsView;
import com.tasf.b2b.application.dto.ShipmentView;
import com.tasf.b2b.application.dto.SimSessionView;
import com.tasf.b2b.application.dto.SnapshotView;
import com.tasf.b2b.domain.simulator.dto.SlaBreachInfo;

import java.util.List;

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
     * Sesión activa del usuario, o null si no tiene ninguna.
     * Útil para reconectar desde otro navegador sin conocer el sessionId.
     */
    SimSessionView getSessionByUser(String username);

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

    /**
     * Historial de transiciones de estado de una maleta, con timestamp (LE-45).
     *
     * @throws IllegalArgumentException si la sesión o la maleta no existen
     */
    BaggageHistoryView getBaggageHistory(String sessionId, String baggageId);

    /**
     * Lista de todos los vuelos del horizonte activo con nivel de ocupación.
     *
     * @throws IllegalArgumentException si la sesión no existe
     */
    List<FlightView> getFlights(String sessionId);

    /**
     * Detalle de un vuelo: mismos campos que la lista + envíos y maletas a bordo.
     *
     * @throws IllegalArgumentException si la sesión no existe o el vuelo no está en el horizonte
     */
    FlightView.DetailView getFlightDetail(String sessionId, String flightId);

    /**
     * Lista de todos los envíos con conteo agregado por estado de sus maletas.
     * Categorías: delivered, noRoute, onTime, late.
     *
     * @throws IllegalArgumentException si la sesión no existe
     */
    List<ShipmentView> getShipments(String sessionId);

    /**
     * Detalle de un envío: cada maleta individualmente con su ruta y estimación de llegada.
     *
     * @throws IllegalArgumentException si la sesión no existe o el envío no existe
     */
    ShipmentView.DetailView getShipmentDetail(String sessionId, String shipmentId);

    /**
     * Diagnóstico forense de un envío: por cada maleta incumplida o sin ruta,
     * reconstruye el contexto y emite un veredicto sobre por qué no se planificó
     * (fallo del planificador vs. infactibilidad real de horario).
     *
     * @throws IllegalArgumentException si la sesión o el envío no existen
     */
    ShipmentDiagnosticsView getShipmentDiagnostics(String sessionId, String shipmentId);

    /** Lista de aeropuertos con carga en tiempo real y nivel de ocupación. */
    List<AirportLiveView> getAirports(String sessionId);

    /** Vuelos futuros que llegarán a este aeropuerto con sus baggages asignados. */
    AirportLiveView.InboundView getAirportInbound(String sessionId, String icao);

    /** Vuelos futuros que saldrán de este aeropuerto con sus baggages asignados. */
    AirportLiveView.OutboundView getAirportOutbound(String sessionId, String icao);

    /** Baggages esperando conexión físicamente en este aeropuerto ahora. */
    AirportLiveView.TransitView getAirportTransit(String sessionId, String icao);

    /** Métricas finales de la simulación. */
    ReportView getReport(String sessionId);

    /**
     * Foto forense de cada incumplimiento de SLA capturada en el instante exacto
     * en que cada maleta cruzó su deadline sin haber sido entregada.
     */
    List<SlaBreachInfo> getSlaBreaches(String sessionId);
}
