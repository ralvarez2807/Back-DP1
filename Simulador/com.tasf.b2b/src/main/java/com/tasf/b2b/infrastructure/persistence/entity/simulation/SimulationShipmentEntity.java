package com.tasf.b2b.infrastructure.persistence.entity.simulation;

import com.tasf.b2b.infrastructure.persistence.entity.base.ShipmentBase;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(schema = "simulation", name = "shipments")
public class SimulationShipmentEntity extends ShipmentBase {

    public SimulationShipmentEntity() {}

    public SimulationShipmentEntity(String id, String originIcao, String destinationIcao,
                                    Instant entryUtc, short quantity, String clientId) {
        super(id, originIcao, destinationIcao, entryUtc, quantity, clientId);
    }
}
