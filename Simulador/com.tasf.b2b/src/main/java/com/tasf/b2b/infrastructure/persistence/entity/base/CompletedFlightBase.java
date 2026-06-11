package com.tasf.b2b.infrastructure.persistence.entity.base;

import jakarta.persistence.*;
import java.time.Instant;

@MappedSuperclass
public abstract class CompletedFlightBase {

    @Id
    @Column(name = "edge_id", length = 32)
    private String edgeId;  // "SKBO-SEQM-19:00-20260102"

    @Column(name = "flight_schedule_id", nullable = false, length = 20)
    private String flightScheduleId;

    @Column(name = "origin_icao", nullable = false, length = 4)
    private String originIcao;

    @Column(name = "destination_icao", nullable = false, length = 4)
    private String destinationIcao;

    @Column(name = "departure_utc", nullable = false)
    private Instant departureUtc;

    @Column(name = "arrival_utc", nullable = false)
    private Instant arrivalUtc;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "final_load", nullable = false)
    private int finalLoad;

    @Column(nullable = false)
    private boolean cancelled;

    protected CompletedFlightBase() {}

    protected CompletedFlightBase(String edgeId, String flightScheduleId,
                                  String originIcao, String destinationIcao,
                                  Instant departureUtc, Instant arrivalUtc,
                                  int capacity, int finalLoad, boolean cancelled) {
        this.edgeId           = edgeId;
        this.flightScheduleId = flightScheduleId;
        this.originIcao       = originIcao;
        this.destinationIcao  = destinationIcao;
        this.departureUtc     = departureUtc;
        this.arrivalUtc       = arrivalUtc;
        this.capacity         = capacity;
        this.finalLoad        = finalLoad;
        this.cancelled        = cancelled;
    }

    public String  getEdgeId()           { return edgeId; }
    public String  getFlightScheduleId() { return flightScheduleId; }
    public String  getOriginIcao()       { return originIcao; }
    public String  getDestinationIcao()  { return destinationIcao; }
    public Instant getDepartureUtc()     { return departureUtc; }
    public Instant getArrivalUtc()       { return arrivalUtc; }
    public int     getCapacity()         { return capacity; }
    public int     getFinalLoad()        { return finalLoad; }
    public boolean isCancelled()         { return cancelled; }
}
