package com.tasf.b2b.infrastructure.persistence.entity.base;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

public class CancellationId implements Serializable {

    private String flightScheduleId;
    private Instant departureUtc;

    public CancellationId() {}

    public CancellationId(String flightScheduleId, Instant departureUtc) {
        this.flightScheduleId = flightScheduleId;
        this.departureUtc     = departureUtc;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CancellationId c)) return false;
        return Objects.equals(flightScheduleId, c.flightScheduleId)
            && Objects.equals(departureUtc, c.departureUtc);
    }

    @Override
    public int hashCode() {
        return Objects.hash(flightScheduleId, departureUtc);
    }
}
