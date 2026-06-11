package com.tasf.b2b.infrastructure.persistence.entity.base;

import jakarta.persistence.*;
import java.time.Instant;

@MappedSuperclass
@IdClass(CancellationId.class)
public abstract class CancellationBase {

    @Id
    @Column(name = "flight_schedule_id", length = 20)
    private String flightScheduleId;

    @Id
    @Column(name = "departure_utc")
    private Instant departureUtc;

    protected CancellationBase() {}

    protected CancellationBase(String flightScheduleId, Instant departureUtc) {
        this.flightScheduleId = flightScheduleId;
        this.departureUtc     = departureUtc;
    }

    public String  getFlightScheduleId() { return flightScheduleId; }
    public Instant getDepartureUtc()     { return departureUtc; }
}
