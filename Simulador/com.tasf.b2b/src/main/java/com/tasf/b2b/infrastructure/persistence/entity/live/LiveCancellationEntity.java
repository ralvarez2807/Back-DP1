package com.tasf.b2b.infrastructure.persistence.entity.live;

import com.tasf.b2b.infrastructure.persistence.entity.base.CancellationBase;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(schema = "live", name = "cancellations")
public class LiveCancellationEntity extends CancellationBase {

    public LiveCancellationEntity() {}

    public LiveCancellationEntity(String flightScheduleId, Instant departureUtc) {
        super(flightScheduleId, departureUtc);
    }
}
