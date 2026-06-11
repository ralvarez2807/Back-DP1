package com.tasf.b2b.application.port.in;

import java.time.LocalDate;
import java.util.List;

/**
 * Puerto de entrada para consultar qué fechas tienen datos de envíos disponibles.
 * El frontend usa esto para poblar el selector de fechas antes de iniciar una sesión.
 *
 * La implementación (TxtAvailableDaysService) escanea los archivos _envios_*.txt
 * y extrae las fechas únicas presentes en los datos.
 */
public interface AvailableDaysPort {

    /**
     * @return fechas disponibles ordenadas de menor a mayor
     */
    List<LocalDate> getAvailableDates();
}
