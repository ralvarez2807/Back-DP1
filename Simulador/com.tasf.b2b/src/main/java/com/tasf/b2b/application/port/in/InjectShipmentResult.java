package com.tasf.b2b.application.port.in;

import java.time.Instant;
import java.util.List;

/**
 * Resultado de inyectar un envío manual: el id generado del envío, los ids de cada
 * maleta creada ({@code <shipmentId>-B<n>}) y los datos confirmados de la orden.
 *
 * @param shipmentId id generado para el envío manual
 * @param baggageIds ids de las maletas creadas
 * @param originIcao ICAO de origen confirmado
 * @param destIcao   ICAO de destino confirmado
 * @param quantity   cantidad de maletas
 * @param entryTime  instante (simulado) en que entró la orden — equivale a "ahora"
 */
public record InjectShipmentResult(
        String       shipmentId,
        List<String> baggageIds,
        String       originIcao,
        String       destIcao,
        int          quantity,
        Instant      entryTime) {}
