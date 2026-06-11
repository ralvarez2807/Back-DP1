package com.tasf.b2b.infrastructure.persistence.repository;

import com.tasf.b2b.infrastructure.persistence.entity.base.CancellationId;
import com.tasf.b2b.infrastructure.persistence.entity.simulation.SimulationCancellationEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface SimulationCancellationJpaRepository
        extends JpaRepository<SimulationCancellationEntity, CancellationId> {

    /** Cancelaciones con salida en [from, to], paginadas (ver DbSimulationCancellationFeed). */
    Slice<SimulationCancellationEntity> findByDepartureUtcGreaterThanEqualAndDepartureUtcLessThanEqual(
            Instant from, Instant to, Pageable pageable);
}
