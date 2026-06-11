package com.tasf.b2b.infrastructure.persistence.repository;

import com.tasf.b2b.infrastructure.persistence.entity.reference.FlightScheduleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlightScheduleJpaRepository extends JpaRepository<FlightScheduleEntity, String> {}
