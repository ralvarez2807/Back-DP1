package com.tasf.b2b.infrastructure.persistence.adapter;

import com.tasf.b2b.infrastructure.persistence.repository.UserJpaRepository;
import com.tasf.b2b.infrastructure.security.UserRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class DbUserRepository implements UserRepository {

    private final UserJpaRepository jpa;

    public DbUserRepository(UserJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<String> findPasswordHash(String username) {
        return jpa.findByUsername(username).map(u -> u.getPasswordHash());
    }
}
