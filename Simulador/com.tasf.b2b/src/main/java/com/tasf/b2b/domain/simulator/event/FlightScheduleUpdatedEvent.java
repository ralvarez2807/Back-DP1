package com.tasf.b2b.domain.simulator.event;

import com.tasf.b2b.domain.model.graph.immovable.FlightScheduleDataDTO;
import com.tasf.b2b.domain.simulator.SimulationClock;

import java.time.Instant;

/**
 * Actualiza horario/capacidad de un vuelo recurrente (LE-12). Si la nueva hora de
 * salida cambia el id (formato ORIG-DEST-HH:mm), el schedule viejo se retira antes
 * de registrar el nuevo. Las instancias ya expandidas y aún no partidas del schedule
 * viejo se cancelan y sus maletas se replanifican (LE-27).
 */
public class FlightScheduleUpdatedEvent extends SimEvent {
    private final String                oldScheduleId;
    private final FlightScheduleDataDTO newSchedule;

    public FlightScheduleUpdatedEvent(Instant simTime, String oldScheduleId,
                                      FlightScheduleDataDTO newSchedule, SimulationClock clock) {
        super(simTime, clock);
        this.oldScheduleId = oldScheduleId;
        this.newSchedule   = newSchedule;
    }

    public String                getOldScheduleId() { return oldScheduleId; }
    public FlightScheduleDataDTO getNewSchedule()    { return newSchedule; }
}
