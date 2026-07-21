package com.tasf.b2b.application.port.in;

/**
 * Orden de inyección de un envío (carga manual de maletas) sobre una sesión activa.
 *
 * <p>A diferencia de los envíos del archivo/BD que alimentan la simulación 5d, estas
 * órdenes las carga un operario en vivo desde la web sobre la "Operación Día a Día":
 * el envío entra al motor con {@code entryDateTimeUtc = ahora} y el optimizador (ALNS)
 * lo enruta de inmediato a vuelos que tengan almacenamiento (capacidad) disponible.</p>
 *
 * @param originIcao       ICAO de origen solicitado. Se usa solo si el operario no
 *                         corresponde a una ciudad (p. ej. el usuario {@code admin});
 *                         para un operario de ciudad, el origen se IMPONE desde su sede.
 * @param destIcao         ICAO del aeropuerto de destino
 * @param quantity         cantidad de maletas (cada unidad se convierte en un baggage)
 * @param clientId         identificador del cliente/operario (informativo)
 * @param operatorUsername username del operario logueado. Si coincide con el nombre de
 *                         una ciudad de la red, esa ciudad es el origen de la orden
 *                         (cada usuario es el operador de su ciudad).
 * @param orderId          id de pedido explícito (opcional). Si viene, se usa como id
 *                         del envío (y por tanto de sus maletas: {@code <orderId>-B<n>}).
 *                         Un id sólo puede reutilizarse una vez que todas las maletas del
 *                         envío anterior con ese id se hayan entregado. Si es null/vacío,
 *                         se genera un id automático {@code MAN-YYYYMMDD-NNNN}.
 */
public record InjectShipmentCommand(
        String originIcao,
        String destIcao,
        int    quantity,
        String clientId,
        String operatorUsername,
        String orderId) {}
