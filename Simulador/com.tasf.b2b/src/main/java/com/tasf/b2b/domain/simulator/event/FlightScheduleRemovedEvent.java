package com.tasf.b2b.domain.simulator.event;

import com.tasf.b2b.domain.simulator.SimulationClock;

import java.time.Instant;

/**
 * Elimina un vuelo recurrente (schedule) de una sesión en curso. Las instancias ya
 * expandidas y aún no partidas del schedule se cancelan y sus maletas se replanifican
 * (igual que en {@link FlightScheduleUpdatedEvent}, pero sin registrar un reemplazo).
 */
public class FlightScheduleRemovedEvent extends SimEvent {
    private final String scheduleId;

    public FlightScheduleRemovedEvent(Instant simTime, String scheduleId, SimulationClock clock) {
        super(simTime, clock);
        this.scheduleId = scheduleId;
    }

    public String getScheduleId() { return scheduleId; }
}
