package com.tasf.b2b.infrastructure.persistence.repository;

import com.tasf.b2b.infrastructure.persistence.entity.base.BaggageRouteLegId;
import com.tasf.b2b.infrastructure.persistence.entity.live.LiveBaggageRouteLegEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LiveBaggageRouteLegJpaRepository
        extends JpaRepository<LiveBaggageRouteLegEntity, BaggageRouteLegId> {

    List<LiveBaggageRouteLegEntity> findByBaggageIdOrderByLegOrderAsc(String baggageId);
}
