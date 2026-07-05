package com.tasf.b2b.infrastructure.persistence.adapter;

import com.tasf.b2b.application.port.in.ManualOrderPort;
import com.tasf.b2b.infrastructure.persistence.entity.live.LiveShipmentEntity;
import com.tasf.b2b.infrastructure.persistence.repository.LiveShipmentJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Persiste las órdenes de la Operación Día a Día en {@code live.shipments} (LE-36).
 * La tabla y el schema ya existían (preparados para el modo tiempo real) pero no
 * estaban conectados a ningún flujo real hasta ahora.
 */
@Component
public class DbManualOrderService implements ManualOrderPort {

    private final LiveShipmentJpaRepository repo;

    public DbManualOrderService(LiveShipmentJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public void recordOrder(String shipmentId, String originIcao, String destIcao,
                            int quantity, String clientId, Instant entryTime) {
        repo.save(new LiveShipmentEntity(shipmentId, originIcao, destIcao, entryTime, (short) quantity, clientId));
    }

    @Override
    public long countOrders() {
        return repo.count();
    }
}
