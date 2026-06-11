//Shipment.java - revisado
package com.tasf.b2b.domain.model.graph.movable;

import com.tasf.b2b.domain.model.graph.immovable.ShipmentDataDTO;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Shipment {
    private final ShipmentDataDTO shipmentData;
    private final Instant deadlineUtc;
    private final String destIcao;
    private List<Baggage> baggages;

    //Constructor
    public Shipment(ShipmentDataDTO shipmentData) {
        this.shipmentData = shipmentData;
        this.deadlineUtc = this.shipmentData.getEntryDateTimeUtc().plus(this.shipmentData.getTypeValue().getMaxDeliveryTime());
        this.destIcao = this.shipmentData.getDestAirport().getIcao();
        this.baggages = new ArrayList<>();

        // Cada unidad del shipment se convierte en un baggage independiente
        for (int i = 0; i < this.shipmentData.getQuantity(); i++) {
            this.baggages.add(new Baggage(this, i + 1));
        }
    }

    //Getters
    public ShipmentDataDTO getShipmentData() {
        return shipmentData;
    }

    public String getDestIcao() {
        return destIcao;
    }

    public Instant getDeadlineUtc() {
        return deadlineUtc;
    }

    public List<Baggage> getBaggages() {
        return Collections.unmodifiableList(this.baggages);
    }

    @Override
    public String toString() {
        return String.format("Shipment[%s | baggages=%d | deadline=%s]",
                this.shipmentData.getId(), this.baggages.size(), this.deadlineUtc);
    }
}
