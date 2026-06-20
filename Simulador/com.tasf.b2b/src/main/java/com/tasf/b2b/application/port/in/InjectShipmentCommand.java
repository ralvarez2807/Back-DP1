package com.tasf.b2b.application.port.in;

/**
 * Orden de inyección de un envío (carga manual de maletas) sobre una sesión activa.
 *
 * <p>A diferencia de los envíos del archivo/BD que alimentan la simulación 5d, estas
 * órdenes las carga un operario en vivo desde la web sobre la "Operación Día a Día":
 * el envío entra al motor con {@code entryDateTimeUtc = ahora} y el optimizador (ALNS)
 * lo enruta de inmediato a vuelos que tengan almacenamiento (capacidad) disponible.</p>
 *
 * @param originIcao ICAO del aeropuerto de origen (almacén desde el que sale la orden)
 * @param destIcao   ICAO del aeropuerto de destino
 * @param quantity   cantidad de maletas (cada unidad se convierte en un baggage)
 * @param clientId   identificador del cliente/operario (informativo)
 */
public record InjectShipmentCommand(
        String originIcao,
        String destIcao,
        int    quantity,
        String clientId) {}
