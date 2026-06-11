package com.tasf.b2b.infrastructure.persistence.entity.base;

import jakarta.persistence.*;

@MappedSuperclass
@IdClass(BaggageRouteLegId.class)
public abstract class BaggageRouteLegBase {

    @Id
    @Column(name = "baggage_id", length = 64)
    private String baggageId;

    @Id
    @Column(name = "leg_order")
    private short legOrder;

    @Column(name = "flight_edge_id", nullable = false, length = 32)
    private String flightEdgeId;

    protected BaggageRouteLegBase() {}

    protected BaggageRouteLegBase(String baggageId, short legOrder, String flightEdgeId) {
        this.baggageId    = baggageId;
        this.legOrder     = legOrder;
        this.flightEdgeId = flightEdgeId;
    }

    public String getBaggageId()    { return baggageId; }
    public short  getLegOrder()     { return legOrder; }
    public String getFlightEdgeId() { return flightEdgeId; }
}
