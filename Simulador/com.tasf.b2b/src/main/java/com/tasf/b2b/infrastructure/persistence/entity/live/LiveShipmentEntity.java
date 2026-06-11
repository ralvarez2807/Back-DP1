package com.tasf.b2b.infrastructure.persistence.entity.live;

import com.tasf.b2b.infrastructure.persistence.entity.base.ShipmentBase;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(schema = "live", name = "shipments")
public class LiveShipmentEntity extends ShipmentBase {

    public LiveShipmentEntity() {}

    public LiveShipmentEntity(String id, String originIcao, String destinationIcao,
                              Instant entryUtc, short quantity, String clientId) {
        super(id, originIcao, destinationIcao, entryUtc, quantity, clientId);
    }
}
