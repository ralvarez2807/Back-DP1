package com.tasf.b2b.infrastructure.persistence.entity.simulation;

import com.tasf.b2b.infrastructure.persistence.entity.base.CompletedFlightBase;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(schema = "simulation", name = "completed_flights")
public class SimulationCompletedFlightEntity extends CompletedFlightBase {

    public SimulationCompletedFlightEntity() {}

    public SimulationCompletedFlightEntity(String edgeId, String flightScheduleId,
                                           String originIcao, String destinationIcao,
                                           Instant departureUtc, Instant arrivalUtc,
                                           int capacity, int finalLoad, boolean cancelled) {
        super(edgeId, flightScheduleId, originIcao, destinationIcao,
              departureUtc, arrivalUtc, capacity, finalLoad, cancelled);
    }
}
