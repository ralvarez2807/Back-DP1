package com.tasf.b2b.infrastructure.persistence.entity.simulation;

import com.tasf.b2b.infrastructure.persistence.entity.base.DeliveredBaggageBase;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(schema = "simulation", name = "delivered_baggages")
public class SimulationDeliveredBaggageEntity extends DeliveredBaggageBase {

    public SimulationDeliveredBaggageEntity() {}

    public SimulationDeliveredBaggageEntity(String baggageId, String shipmentId,
                                            String originIcao, String destinationIcao,
                                            Instant entryUtc, Instant deadlineUtc, Instant deliveredUtc) {
        super(baggageId, shipmentId, originIcao, destinationIcao, entryUtc, deadlineUtc, deliveredUtc);
    }
}
