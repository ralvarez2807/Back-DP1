package com.tasf.b2b.infrastructure.persistence.repository;

import com.tasf.b2b.infrastructure.persistence.entity.base.CancellationId;
import com.tasf.b2b.infrastructure.persistence.entity.live.LiveCancellationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LiveCancellationJpaRepository
        extends JpaRepository<LiveCancellationEntity, CancellationId> {}
