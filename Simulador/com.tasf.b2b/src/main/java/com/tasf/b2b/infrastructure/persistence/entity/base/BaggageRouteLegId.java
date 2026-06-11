package com.tasf.b2b.infrastructure.persistence.entity.base;

import java.io.Serializable;
import java.util.Objects;

public class BaggageRouteLegId implements Serializable {

    private String baggageId;
    private short  legOrder;

    public BaggageRouteLegId() {}

    public BaggageRouteLegId(String baggageId, short legOrder) {
        this.baggageId = baggageId;
        this.legOrder  = legOrder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BaggageRouteLegId b)) return false;
        return legOrder == b.legOrder && Objects.equals(baggageId, b.baggageId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(baggageId, legOrder);
    }
}
