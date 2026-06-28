package com.tasf.b2b.domain.simulator.event;

import com.tasf.b2b.domain.model.graph.movable.Baggage;
import com.tasf.b2b.domain.simulator.SimulationClock;

import java.time.Instant;

/**
 * Se dispara en el instante del deadline de una maleta. Si en ese momento la maleta
 * aún no fue entregada, es un incumplimiento de SLA: se captura la foto forense del
 * momento exacto en que el contador "SLA vencido" sube.
 */
public class SlaDeadlineEvent extends SimEvent {
    private final Baggage baggage;

    public SlaDeadlineEvent(Instant simTime, Baggage baggage, SimulationClock clock) {
        super(simTime, clock);
        this.baggage = baggage;
    }

    public Baggage getBaggage() { return baggage; }
}
