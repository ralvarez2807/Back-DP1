package com.tasf.b2b.infrastructure.persistence.entity.reference;

import jakarta.persistence.*;
import java.time.LocalTime;

@Entity
@Table(schema = "reference", name = "flight_schedules")
public class FlightScheduleEntity {

    @Id
    @Column(length = 20)
    private String id;  // "SKBO-SEQM-19:00"

    @Column(name = "origin_icao", nullable = false, length = 4)
    private String originIcao;

    @Column(name = "destination_icao", nullable = false, length = 4)
    private String destinationIcao;

    @Column(name = "departure_local", nullable = false)
    private LocalTime departureLocal;

    @Column(name = "arrival_local", nullable = false)
    private LocalTime arrivalLocal;

    @Column(nullable = false)
    private int capacity;

    public FlightScheduleEntity() {}

    public FlightScheduleEntity(String id, String originIcao, String destinationIcao,
                                LocalTime departureLocal, LocalTime arrivalLocal, int capacity) {
        this.id              = id;
        this.originIcao      = originIcao;
        this.destinationIcao = destinationIcao;
        this.departureLocal  = departureLocal;
        this.arrivalLocal    = arrivalLocal;
        this.capacity        = capacity;
    }

    public String    getId()              { return id; }
    public String    getOriginIcao()      { return originIcao; }
    public String    getDestinationIcao() { return destinationIcao; }
    public LocalTime getDepartureLocal()  { return departureLocal; }
    public LocalTime getArrivalLocal()    { return arrivalLocal; }
    public int       getCapacity()        { return capacity; }
}
