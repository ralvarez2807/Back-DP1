package com.tasf.b2b.infrastructure.security;

import java.util.Optional;

public interface UserRepository {
    Optional<String> findPasswordHash(String username);
}
