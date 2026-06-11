package com.tasf.b2b.infrastructure.persistence.entity.live;

import com.tasf.b2b.infrastructure.persistence.entity.base.DeliveredBaggageBase;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(schema = "live", name = "delivered_baggages")
public class LiveDeliveredBaggageEntity extends DeliveredBaggageBase {

    public LiveDeliveredBaggageEntity() {}

    public LiveDeliveredBaggageEntity(String baggageId, String shipmentId,
                                      String originIcao, String destinationIcao,
                                      Instant entryUtc, Instant deadlineUtc, Instant deliveredUtc) {
        super(baggageId, shipmentId, originIcao, destinationIcao, entryUtc, deadlineUtc, deliveredUtc);
    }
}
