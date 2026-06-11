package com.tasf.b2b.infrastructure.persistence.repository;

import com.tasf.b2b.infrastructure.persistence.entity.simulation.SimulationCompletedFlightEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulationCompletedFlightJpaRepository
        extends JpaRepository<SimulationCompletedFlightEntity, String> {}
