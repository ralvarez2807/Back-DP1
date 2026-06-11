package com.tasf.b2b.domain.simulator.event;

import com.tasf.b2b.domain.model.graph.immovable.ShipmentDataDTO;
import com.tasf.b2b.domain.simulator.SimulationClock;

import java.time.Instant;

/**
 * Llega un nuevo envío al aeropuerto de origen.
 * Inyectable desde el hilo externo de inyección de maletas.
 */
public class NewShipmentEvent extends SimEvent {
    private final ShipmentDataDTO shipmentData;

    public NewShipmentEvent(Instant simTime, ShipmentDataDTO shipmentData, SimulationClock clock) {
        super(simTime, clock);
        this.shipmentData = shipmentData;
    }

    public ShipmentDataDTO getShipmentData() { return shipmentData; }
}
