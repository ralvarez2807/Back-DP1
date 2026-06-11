package com.tasf.b2b.infrastructure.persistence.repository;

import com.tasf.b2b.infrastructure.persistence.entity.base.ShipmentId;
import com.tasf.b2b.infrastructure.persistence.entity.live.LiveShipmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LiveShipmentJpaRepository extends JpaRepository<LiveShipmentEntity, ShipmentId> {

    LiveShipmentEntity findTopByOrderByEntryUtcDesc();
}
