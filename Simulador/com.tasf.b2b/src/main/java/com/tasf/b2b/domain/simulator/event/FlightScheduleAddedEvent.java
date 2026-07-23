package com.tasf.b2b.domain.simulator.event;

import com.tasf.b2b.domain.model.graph.immovable.FlightScheduleDataDTO;
import com.tasf.b2b.domain.simulator.SimulationClock;

import java.time.Instant;

/**
 * Da de alta un vuelo recurrente (schedule) en una sesión en curso (LE-10). Registra el
 * schedule para las próximas expansiones del horizonte y además materializa sus instancias
 * dentro de la ventana ya expandida, de modo que el ALNS pueda usarlas en el ciclo siguiente
 * en vez de esperar a que el horizonte rodante alcance el vuelo.
 *
 * <p>Contraparte de {@link FlightScheduleRemovedEvent}: no cancela nada ni replanifica por
 * pérdida de capacidad — solo agrega opciones de ruta.</p>
 */
public class FlightScheduleAddedEvent extends SimEvent {
    private final FlightScheduleDataDTO schedule;

    public FlightScheduleAddedEvent(Instant simTime, FlightScheduleDataDTO schedule, SimulationClock clock) {
        super(simTime, clock);
        this.schedule = schedule;
    }

    public FlightScheduleDataDTO getSchedule() { return schedule; }
}
