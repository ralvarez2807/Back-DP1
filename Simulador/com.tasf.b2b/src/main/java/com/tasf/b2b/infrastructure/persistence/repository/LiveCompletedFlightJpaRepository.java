package com.tasf.b2b.infrastructure.persistence.repository;

import com.tasf.b2b.infrastructure.persistence.entity.live.LiveCompletedFlightEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LiveCompletedFlightJpaRepository
        extends JpaRepository<LiveCompletedFlightEntity, String> {}
