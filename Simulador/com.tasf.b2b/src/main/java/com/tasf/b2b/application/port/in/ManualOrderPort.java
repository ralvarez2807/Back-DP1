package com.tasf.b2b.application.port.in;

import java.time.Instant;

/**
 * Persistencia de las órdenes cargadas manualmente en la Operación Día a Día
 * (LE-36). Antes de esto, {@code POST /operations/orders} solo vivía en RAM
 * (motor de simulación) y se perdía al reiniciar el servidor.
 */
public interface ManualOrderPort {

    /** Persiste una orden ya inyectada en la simulación (no falla la operación si la orden ya existe). */
    void recordOrder(String shipmentId, String originIcao, String destIcao,
                     int quantity, String clientId, Instant entryTime);

    /** Total histórico de órdenes registradas en BD. */
    long countOrders();
}
