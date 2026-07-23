package com.tasf.b2b.application.port.in;

import com.tasf.b2b.domain.model.graph.immovable.FlightScheduleDataDTO;

import java.time.LocalTime;

/**
 * Puerto de administración de vuelos (schedules recurrentes).
 *
 * <p>Permite dar de alta un vuelo nuevo (LE-10) y modificar el horario/capacidad de
 * uno existente (LE-12), disparando la replanificación de las maletas afectadas en
 * toda sesión activa (LE-27).</p>
 */
public interface FlightAdminPort {

    /**
     * Crea un vuelo (schedule) nuevo. Queda disponible tanto para las sesiones que se creen
     * a partir de este momento como para las que ya están en ejecución: en estas últimas se
     * materializan sus instancias dentro del horizonte ya expandido y se dispara un ciclo de
     * optimización, de modo que las maletas pendientes puedan usarlo sin reiniciar (LE-10).
     *
     * @throws IllegalArgumentException si el ICAO de origen o destino no existe
     * @throws IllegalStateException    si ya existe un schedule con ese id (ORIG-DEST-HH:mm)
     */
    FlightScheduleDataDTO createFlight(String originIcao, String destIcao,
                                       LocalTime depTimeLocal, LocalTime arrTimeLocal, int capacity);

    /**
     * Resincroniza el catálogo en memoria con lo que hay en BD y propaga el diff (altas,
     * bajas y cambios) a las sesiones activas. Pensado para la carga masiva por archivo,
     * que escribe directo en BD sin pasar por los métodos individuales.
     */
    void reloadCatalogFromDb();

    /**
     * Modifica horario y/o capacidad de un schedule existente. Campos {@code null} se
     * dejan sin cambiar. Si la hora de salida cambia, el id del schedule cambia
     * también (formato ORIG-DEST-HH:mm) — se retira el viejo y se registra el nuevo.
     *
     * <p>Propaga el cambio a toda sesión activa: cancela las instancias futuras ya
     * expandidas del schedule viejo (replanificando sus maletas) y registra el nuevo
     * schedule para las próximas expansiones del horizonte.</p>
     *
     * @throws IllegalArgumentException si el scheduleId no existe
     */
    FlightScheduleDataDTO updateFlight(String scheduleId, LocalTime depTimeLocal,
                                       LocalTime arrTimeLocal, Integer capacity);

    /**
     * Elimina un vuelo (schedule) del catálogo: lo borra de la BD y de la lista
     * compartida en memoria (deja de estar disponible para nuevas sesiones) y propaga
     * el borrado a toda sesión activa, cancelando sus instancias futuras ya expandidas
     * y replanificando las maletas afectadas (LE-27).
     *
     * @throws IllegalArgumentException si el scheduleId no existe
     */
    void deleteFlight(String scheduleId);
}
