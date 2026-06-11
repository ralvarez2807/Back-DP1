package com.tasf.b2b.infrastructure.persistence.repository;

import com.tasf.b2b.infrastructure.persistence.entity.simulation.SimulationDeliveredBaggageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulationDeliveredBaggageJpaRepository
        extends JpaRepository<SimulationDeliveredBaggageEntity, String> {}
