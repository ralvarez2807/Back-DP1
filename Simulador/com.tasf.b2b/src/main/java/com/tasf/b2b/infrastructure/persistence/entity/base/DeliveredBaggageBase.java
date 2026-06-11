package com.tasf.b2b.infrastructure.persistence.entity.base;

import jakarta.persistence.*;
import java.time.Instant;

@MappedSuperclass
public abstract class DeliveredBaggageBase {

    @Id
    @Column(name = "baggage_id", length = 64)
    private String baggageId;  // "<shipmentId>-B<n>"

    @Column(name = "shipment_id", nullable = false, length = 64)
    private String shipmentId;

    @Column(name = "origin_icao", nullable = false, length = 4)
    private String originIcao;

    @Column(name = "destination_icao", nullable = false, length = 4)
    private String destinationIcao;

    @Column(name = "entry_utc", nullable = false)
    private Instant entryUtc;

    @Column(name = "deadline_utc", nullable = false)
    private Instant deadlineUtc;

    @Column(name = "delivered_utc", nullable = false)
    private Instant deliveredUtc;

    @Column(name = "on_time", nullable = false)
    private boolean onTime;

    protected DeliveredBaggageBase() {}

    protected DeliveredBaggageBase(String baggageId, String shipmentId,
                                   String originIcao, String destinationIcao,
                                   Instant entryUtc, Instant deadlineUtc, Instant deliveredUtc) {
        this.baggageId       = baggageId;
        this.shipmentId      = shipmentId;
        this.originIcao      = originIcao;
        this.destinationIcao = destinationIcao;
        this.entryUtc        = entryUtc;
        this.deadlineUtc     = deadlineUtc;
        this.deliveredUtc    = deliveredUtc;
        this.onTime          = !deliveredUtc.isAfter(deadlineUtc);
    }

    public String  getBaggageId()       { return baggageId; }
    public String  getShipmentId()      { return shipmentId; }
    public String  getOriginIcao()      { return originIcao; }
    public String  getDestinationIcao() { return destinationIcao; }
    public Instant getEntryUtc()        { return entryUtc; }
    public Instant getDeadlineUtc()     { return deadlineUtc; }
    public Instant getDeliveredUtc()    { return deliveredUtc; }
    public boolean isOnTime()           { return onTime; }
}
