package com.tasf.b2b.application.port.in;

/**
 * Puerto de administración de aeropuertos.
 *
 * <p>Solo se permite <b>modificar la capacidad de almacén</b> de un aeropuerto existente.
 * No se pueden crear ni eliminar aeropuertos: la red de aeropuertos es fija.</p>
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
}
