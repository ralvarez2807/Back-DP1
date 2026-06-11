package com.tasf.b2b.domain.simulator.event;

import com.tasf.b2b.domain.model.graph.movable.Baggage;
import com.tasf.b2b.domain.simulator.SimulationClock;

import java.time.Instant;

/**
 * Se dispara pickupMinutes después de que la maleta aterriza en su destino final.
 * Modela el tiempo de recogida en el aeropuerto destino.
 */
public class BaggagePickupEvent extends SimEvent {
    private final Baggage baggage;
    private final String  destIcao;

    public BaggagePickupEvent(Instant simTime, Baggage baggage, String destIcao, SimulationClock clock) {
        super(simTime, clock);
        this.baggage  = baggage;
        this.destIcao = destIcao;
    }

    public Baggage getBaggage()  { return baggage; }
    public String  getDestIcao() { return destIcao; }
}
