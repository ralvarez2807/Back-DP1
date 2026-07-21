package com.tasf.b2b.infrastructure.persistence.adapter;

import com.tasf.b2b.application.port.in.FlightAdminPort;
import com.tasf.b2b.application.usecase.RunSimulationUseCase;
import com.tasf.b2b.application.usecase.SimulationRegistry;
import com.tasf.b2b.application.usecase.SimulationSession;
import com.tasf.b2b.domain.model.graph.immovable.AirportDataDTO;
import com.tasf.b2b.domain.model.graph.immovable.FlightScheduleDataDTO;
import com.tasf.b2b.domain.simulator.SimulationClock;
import com.tasf.b2b.domain.simulator.event.FlightScheduleRemovedEvent;
import com.tasf.b2b.domain.simulator.event.FlightScheduleUpdatedEvent;
import com.tasf.b2b.infrastructure.persistence.entity.reference.FlightScheduleEntity;
import com.tasf.b2b.infrastructure.persistence.repository.FlightScheduleJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Map;

/**
 * Alta y modificación de vuelos (schedules recurrentes): persiste en BD y refleja el
 * cambio en la lista compartida en memoria (nuevas sesiones) y, para modificaciones,
 * en toda sesión activa (LE-12, LE-27).
 */
@Component
public class DbFlightAdminService implements FlightAdminPort {

    private final FlightScheduleJpaRepository flightRepo;
    private final RunSimulationUseCase        runSimulationUseCase;
    private final SimulationRegistry          registry;

    public DbFlightAdminService(FlightScheduleJpaRepository flightRepo,
                                RunSimulationUseCase runSimulationUseCase,
                                SimulationRegistry registry) {
        this.flightRepo           = flightRepo;
        this.runSimulationUseCase = runSimulationUseCase;
        this.registry             = registry;
    }

    @Override
    @Transactional
    public FlightScheduleDataDTO createFlight(String originIcao, String destIcao,
                                              LocalTime depTimeLocal, LocalTime arrTimeLocal, int capacity) {
        String orig = originIcao.trim().toUpperCase();
        String dest = destIcao.trim().toUpperCase();

        Map<String, AirportDataDTO> airports = runSimulationUseCase.getAirports();
        AirportDataDTO originAirport = airports.get(orig);
        AirportDataDTO destAirport   = airports.get(dest);
        if (originAirport == null) throw new IllegalArgumentException("Aeropuerto de origen no existe: " + orig);
        if (destAirport == null)   throw new IllegalArgumentException("Aeropuerto de destino no existe: " + dest);

        FlightScheduleDataDTO dto = new FlightScheduleDataDTO(
                originAirport, destAirport, depTimeLocal, arrTimeLocal, capacity);

        if (flightRepo.existsById(dto.getId()))
            throw new IllegalStateException("Ya existe un vuelo programado con id: " + dto.getId());

        flightRepo.save(new FlightScheduleEntity(
                dto.getId(), orig, dest, depTimeLocal, arrTimeLocal, capacity));

        // Visible para toda sesión que se cree a partir de ahora (no retroactivo).
        runSimulationUseCase.getFlights().add(dto);

        System.out.printf("[FLIGHTS] Vuelo %s creado%n", dto.getId());
        return dto;
    }

    @Override
    @Transactional
    public FlightScheduleDataDTO updateFlight(String scheduleId, LocalTime depTimeLocal,
                                              LocalTime arrTimeLocal, Integer capacity) {
        FlightScheduleEntity entity = flightRepo.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("Vuelo no encontrado: " + scheduleId));

        LocalTime newDep = depTimeLocal != null ? depTimeLocal : entity.getDepartureLocal();
        LocalTime newArr = arrTimeLocal != null ? arrTimeLocal : entity.getArrivalLocal();
        int       newCap = capacity     != null ? capacity     : entity.getCapacity();

        Map<String, AirportDataDTO> airports = runSimulationUseCase.getAirports();
        AirportDataDTO originAirport = airports.get(entity.getOriginIcao());
        AirportDataDTO destAirport   = airports.get(entity.getDestinationIcao());

        FlightScheduleDataDTO updated = new FlightScheduleDataDTO(
                originAirport, destAirport, newDep, newArr, newCap);

        // El id depende de la hora de salida (ORIG-DEST-HH:mm): si cambió, se retira
        // la fila vieja y se inserta una nueva; si no, se reemplaza en el mismo id.
        flightRepo.deleteById(scheduleId);
        flightRepo.save(new FlightScheduleEntity(
                updated.getId(), entity.getOriginIcao(), entity.getDestinationIcao(), newDep, newArr, newCap));

        // Lista compartida (nuevas sesiones a partir de ahora).
        runSimulationUseCase.getFlights().removeIf(f -> f.getId().equals(scheduleId));
        runSimulationUseCase.getFlights().add(updated);

        // Sesiones activas: cada una recibe el evento en su propio hilo runner
        // (la mutación del grafo solo es segura desde ahí).
        for (SimulationSession session : registry.all()) {
            SimulationClock clock = session.getRunner().getClock();
            session.getRunner().submit(new FlightScheduleUpdatedEvent(clock.now(), scheduleId, updated, clock));
        }

        System.out.printf("[FLIGHTS] Vuelo %s actualizado → %s (%d sesión(es) activa(s) notificada(s))%n",
                scheduleId, updated.getId(), registry.all().size());
        return updated;
    }

    @Override
    @Transactional
    public void deleteFlight(String scheduleId) {
        if (!flightRepo.existsById(scheduleId))
            throw new IllegalArgumentException("Vuelo no encontrado: " + scheduleId);

        flightRepo.deleteById(scheduleId);

        // Lista compartida (nuevas sesiones a partir de ahora ya no lo ven).
        runSimulationUseCase.getFlights().removeIf(f -> f.getId().equals(scheduleId));

        // Sesiones activas: cada una recibe el evento en su propio hilo runner
        // (la mutación del grafo solo es segura desde ahí). Cancela las instancias
        // futuras del schedule y replanifica sus maletas (LE-27).
        for (SimulationSession session : registry.all()) {
            SimulationClock clock = session.getRunner().getClock();
            session.getRunner().submit(new FlightScheduleRemovedEvent(clock.now(), scheduleId, clock));
        }

        System.out.printf("[FLIGHTS] Vuelo %s eliminado (%d sesión(es) activa(s) notificada(s))%n",
                scheduleId, registry.all().size());
    }
}
