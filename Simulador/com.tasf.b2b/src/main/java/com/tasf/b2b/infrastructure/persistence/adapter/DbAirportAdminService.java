package com.tasf.b2b.infrastructure.persistence.adapter;

import com.tasf.b2b.application.port.in.AirportAdminPort;
import com.tasf.b2b.application.usecase.RunSimulationUseCase;
import com.tasf.b2b.domain.model.graph.immovable.AirportDataDTO;
import com.tasf.b2b.infrastructure.persistence.entity.reference.AirportEntity;
import com.tasf.b2b.infrastructure.persistence.repository.AirportJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Modifica la capacidad de almacén de un aeropuerto: la persiste en BD y la refleja
 * en vivo en el mapa en memoria que comparten todas las sesiones (incluida la
 * Operación Día a Día), de modo que el dashboard muestra el nuevo límite al instante.
 */
@Component
public class DbAirportAdminService implements AirportAdminPort {

    private final AirportJpaRepository  airportRepo;
    private final RunSimulationUseCase  runSimulationUseCase;

    public DbAirportAdminService(AirportJpaRepository airportRepo,
                                 RunSimulationUseCase runSimulationUseCase) {
        this.airportRepo          = airportRepo;
        this.runSimulationUseCase = runSimulationUseCase;
    }

    @Override
    @Transactional
    public void updateCapacity(String icao, int capacity) {
        AirportEntity entity = airportRepo.findById(icao)
                .orElseThrow(() -> new IllegalArgumentException("Aeropuerto no encontrado: " + icao));

        entity.setCapacity(capacity);
        airportRepo.save(entity);

        // Reflejo en vivo: el grafo de cada sesión comparte esta misma instancia.
        AirportDataDTO inMemory = runSimulationUseCase.getAirports().get(icao);
        if (inMemory != null) inMemory.setCapacity(capacity);

        System.out.printf("[AIRPORTS] Capacidad de %s actualizada a %d%n", icao, capacity);
    }
}
