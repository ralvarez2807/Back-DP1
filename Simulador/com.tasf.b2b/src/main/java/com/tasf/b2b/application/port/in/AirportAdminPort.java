package com.tasf.b2b.application.port.in;

import com.tasf.b2b.domain.model.graph.immovable.AirportDataDTO;

/**
 * Puerto de administración de aeropuertos.
 *
 * <p>Permite modificar la capacidad de almacén de un aeropuerto existente, y dar de
 * alta un aeropuerto nuevo (exigencias LE-10/LE-13/LE-14/LE-15). No hay eliminación:
 * la red de aeropuertos solo crece.</p>
 */
public interface AirportAdminPort {

    /**
     * Actualiza la capacidad de almacén de un aeropuerto, persistiéndola y reflejándola
     * en vivo en las sesiones activas (incluida la Operación Día a Día).
     *
     * @param icao     ICAO del aeropuerto (debe existir)
     * @param capacity nueva capacidad (> 0)
     * @throws IllegalArgumentException si el aeropuerto no existe
     */
    void updateCapacity(String icao, int capacity);

    /**
     * Crea un aeropuerto nuevo. Se persiste en BD y queda disponible de inmediato
     * para las sesiones que se creen a partir de este momento (no se propaga a
     * sesiones ya en ejecución).
     *
     * @throws IllegalStateException si ya existe un aeropuerto con ese ICAO
     */
    AirportDataDTO createAirport(String icao, String city, String country, String continent,
                                 String shortName, int gmtOffset, int capacity,
                                 double latitude, double longitude);
}
