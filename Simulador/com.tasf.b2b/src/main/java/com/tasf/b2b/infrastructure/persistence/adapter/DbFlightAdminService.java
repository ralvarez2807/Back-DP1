package com.tasf.b2b.infrastructure.persistence.adapter;

import com.tasf.b2b.application.port.in.FlightAdminPort;
import com.tasf.b2b.application.usecase.RunSimulationUseCase;
import com.tasf.b2b.application.usecase.SimulationRegistry;
import com.tasf.b2b.application.usecase.SimulationSession;
import com.tasf.b2b.domain.model.graph.immovable.AirportDataDTO;
import com.tasf.b2b.domain.model.graph.immovable.FlightScheduleDataDTO;
import com.tasf.b2b.domain.simulator.SimulationClock;
import com.tasf.b2b.domain.simulator.event.FlightScheduleAddedEvent;
import com.tasf.b2b.domain.simulator.event.FlightScheduleRemovedEvent;
import com.tasf.b2b.domain.simulator.event.FlightScheduleUpdatedEvent;
import com.tasf.b2b.infrastructure.persistence.entity.reference.FlightScheduleEntity;
import com.tasf.b2b.infrastructure.persistence.repository.FlightScheduleJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

        // Lista compartida (nuevas sesiones a partir de ahora).
        runSimulationUseCase.getFlights().add(dto);

        // Sesiones activas: cada una recibe el evento en su propio hilo runner
        // (la mutación del grafo solo es segura desde ahí). Materializa las instancias
        // dentro del horizonte ya expandido y corre un ciclo de optimización, así el
        // vuelo queda disponible para la planificación sin reiniciar (LE-10, LE-27).
        for (SimulationSession session : registry.all()) {
            SimulationClock clock = session.getRunner().getClock();
            session.getRunner().submit(new FlightScheduleAddedEvent(clock.now(), dto, clock));
        }

        System.out.printf("[FLIGHTS] Vuelo %s creado (%d sesión(es) activa(s) notificada(s))%n",
                dto.getId(), registry.all().size());
        return dto;
    }

    /**
     * Recarga el catálogo de vuelos desde la BD hacia la lista compartida en memoria y
     * propaga el diff a las sesiones activas. Lo usa la carga masiva por archivo, que
     * escribe directo en BD y de otro modo dejaría la RAM desincronizada hasta el
     * siguiente reinicio del backend.
     */
    @Override
    @Transactional
    public void reloadCatalogFromDb() {
        Map<String, AirportDataDTO> airports = runSimulationUseCase.getAirports();

        Map<String, FlightScheduleDataDTO> fresh = new LinkedHashMap<>();
        for (FlightScheduleEntity e : flightRepo.findAll()) {
            AirportDataDTO orig = airports.get(e.getOriginIcao());
            AirportDataDTO dest = airports.get(e.getDestinationIcao());
            // Aeropuerto no presente en el catálogo en memoria: se ignora en vez de
            // tumbar la recarga completa (la carga de aeropuertos lo resolverá).
            if (orig == null || dest == null) continue;

            FlightScheduleDataDTO dto = new FlightScheduleDataDTO(
                    orig, dest, e.getDepartureLocal(), e.getArrivalLocal(), e.getCapacity());
            fresh.put(dto.getId(), dto);
        }

        List<FlightScheduleDataDTO> shared = runSimulationUseCase.getFlights();
        Map<String, FlightScheduleDataDTO> current = new LinkedHashMap<>();
        for (FlightScheduleDataDTO f : shared) current.put(f.getId(), f);

        List<FlightScheduleDataDTO> added   = new ArrayList<>();
        List<FlightScheduleDataDTO> changed = new ArrayList<>();
        for (FlightScheduleDataDTO f : fresh.values()) {
            FlightScheduleDataDTO before = current.get(f.getId());
            if (before == null)            added.add(f);
            else if (differs(before, f))   changed.add(f);
        }
        List<String> removed = current.keySet().stream()
                .filter(id -> !fresh.containsKey(id))
                .toList();

        // Se aplica el diff en vez de vaciar y repoblar: la lista la leen en caliente los
        // hilos que arrancan sesiones, y un clear() dejaría una ventana con el catálogo vacío.
        Set<String> replacedIds = new HashSet<>(removed);
        changed.forEach(f -> replacedIds.add(f.getId()));
        shared.removeIf(f -> replacedIds.contains(f.getId()));
        shared.addAll(added);
        shared.addAll(changed);

        for (SimulationSession session : registry.all()) {
            SimulationClock clock = session.getRunner().getClock();
            for (String id : removed)
                session.getRunner().submit(new FlightScheduleRemovedEvent(clock.now(), id, clock));
            for (FlightScheduleDataDTO f : changed)
                session.getRunner().submit(new FlightScheduleUpdatedEvent(clock.now(), f.getId(), f, clock));
            for (FlightScheduleDataDTO f : added)
                session.getRunner().submit(new FlightScheduleAddedEvent(clock.now(), f, clock));
        }

        System.out.printf("[FLIGHTS] Catálogo recargado: %d vuelos (+%d, ~%d, -%d) — %d sesión(es) activa(s) notificada(s)%n",
                fresh.size(), added.size(), changed.size(), removed.size(), registry.all().size());
    }

    // El id ya codifica origen, destino y hora de salida: solo pueden diferir el resto.
    private static boolean differs(FlightScheduleDataDTO a, FlightScheduleDataDTO b) {
        return a.getCapacity() != b.getCapacity()
                || !a.getArrivalTimeLocal().equals(b.getArrivalTimeLocal());
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
