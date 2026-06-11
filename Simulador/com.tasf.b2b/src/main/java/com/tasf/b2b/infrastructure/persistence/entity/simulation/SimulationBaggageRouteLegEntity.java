package com.tasf.b2b.infrastructure.persistence.entity.simulation;

import com.tasf.b2b.infrastructure.persistence.entity.base.BaggageRouteLegBase;
import jakarta.persistence.*;

@Entity
@Table(schema = "simulation", name = "baggage_route_legs")
public class SimulationBaggageRouteLegEntity extends BaggageRouteLegBase {

    public SimulationBaggageRouteLegEntity() {}

    public SimulationBaggageRouteLegEntity(String baggageId, short legOrder, String flightEdgeId) {
        super(baggageId, legOrder, flightEdgeId);
    }
}
