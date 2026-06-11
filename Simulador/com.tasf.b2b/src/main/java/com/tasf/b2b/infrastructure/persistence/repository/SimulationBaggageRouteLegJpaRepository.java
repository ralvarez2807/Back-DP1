package com.tasf.b2b.infrastructure.persistence.repository;

import com.tasf.b2b.infrastructure.persistence.entity.base.BaggageRouteLegId;
import com.tasf.b2b.infrastructure.persistence.entity.simulation.SimulationBaggageRouteLegEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SimulationBaggageRouteLegJpaRepository
        extends JpaRepository<SimulationBaggageRouteLegEntity, BaggageRouteLegId> {

    List<SimulationBaggageRouteLegEntity> findByBaggageIdOrderByLegOrderAsc(String baggageId);
}
