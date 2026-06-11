package com.tasf.b2b.infrastructure.persistence.entity.live;

import com.tasf.b2b.infrastructure.persistence.entity.base.BaggageRouteLegBase;
import jakarta.persistence.*;

@Entity
@Table(schema = "live", name = "baggage_route_legs")
public class LiveBaggageRouteLegEntity extends BaggageRouteLegBase {

    public LiveBaggageRouteLegEntity() {}

    public LiveBaggageRouteLegEntity(String baggageId, short legOrder, String flightEdgeId) {
        super(baggageId, legOrder, flightEdgeId);
    }
}
