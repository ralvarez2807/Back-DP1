package com.tasf.b2b.infrastructure.persistence.entity.simulation;

import com.tasf.b2b.infrastructure.persistence.entity.base.CancellationBase;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(schema = "simulation", name = "cancellations")
public class SimulationCancellationEntity extends CancellationBase {

    public SimulationCancellationEntity() {}

    public SimulationCancellationEntity(String flightScheduleId, Instant departureUtc) {
        super(flightScheduleId, departureUtc);
    }
}
