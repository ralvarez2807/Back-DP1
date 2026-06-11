package com.tasf.b2b.infrastructure.persistence.repository;

import com.tasf.b2b.infrastructure.persistence.entity.live.LiveDeliveredBaggageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LiveDeliveredBaggageJpaRepository
        extends JpaRepository<LiveDeliveredBaggageEntity, String> {}
