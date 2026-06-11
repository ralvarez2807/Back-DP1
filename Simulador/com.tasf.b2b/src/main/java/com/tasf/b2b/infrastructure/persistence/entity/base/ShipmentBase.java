package com.tasf.b2b.infrastructure.persistence.entity.base;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;

@MappedSuperclass
public abstract class ShipmentBase {

    @EmbeddedId
    private ShipmentId shipmentId;

    @Column(name = "destination_icao", nullable = false, length = 4)
    private String destinationIcao;

    @Column(name = "entry_utc", nullable = false)
    private Instant entryUtc;

    @Column(nullable = false)
    private short quantity;

    @Column(name = "client_id", length = 64)
    private String clientId;

    protected ShipmentBase() {}

    protected ShipmentBase(String id, String originIcao, String destinationIcao,
                           Instant entryUtc, short quantity, String clientId) {
        this.shipmentId      = new ShipmentId(id, originIcao);
        this.destinationIcao = destinationIcao;
        this.entryUtc        = entryUtc;
        this.quantity        = quantity;
        this.clientId        = clientId;
    }

    public String  getId()              { return shipmentId.getId(); }
    public String  getOriginIcao()      { return shipmentId.getOriginIcao(); }
    public String  getDestinationIcao() { return destinationIcao; }
    public Instant getEntryUtc()        { return entryUtc; }
    public short   getQuantity()        { return quantity; }
    public String  getClientId()        { return clientId; }
    public ShipmentId getShipmentId()   { return shipmentId; }
}
