package com.tasf.b2b.infrastructure.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

// Reemplazado por DbUserRepository. Se conserva como herramienta de importación inicial (seed).
public class JsonUserRepository implements UserRepository {

    private final Map<String, String> users;

    public JsonUserRepository() throws IOException {
        var mapper   = new ObjectMapper();
        var resource = new ClassPathResource("users.json");
        List<Map<String, String>> entries = mapper.readValue(
                resource.getInputStream(),
                new TypeReference<>() {}
        );
        this.users = entries.stream()
                .collect(Collectors.toMap(
                        e -> e.get("username"),
                        e -> e.get("passwordHash")
                ));
    }

    @Override
    public Optional<String> findPasswordHash(String username) {
        return Optional.ofNullable(users.get(username));
    }
}
