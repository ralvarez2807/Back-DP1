package com.tasf.b2b.infrastructure.persistence.repository;

import com.tasf.b2b.infrastructure.persistence.entity.system.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<UserEntity, Integer> {
    Optional<UserEntity> findByUsername(String username);
}
