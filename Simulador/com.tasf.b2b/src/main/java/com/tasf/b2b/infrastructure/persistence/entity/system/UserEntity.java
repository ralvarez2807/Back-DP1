package com.tasf.b2b.infrastructure.persistence.entity.system;

import jakarta.persistence.*;

@Entity
@Table(name = "users")  // public schema (default)
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 72)
    private String passwordHash;

    public UserEntity() {}

    public UserEntity(String username, String passwordHash) {
        this.username     = username;
        this.passwordHash = passwordHash;
    }

    public Integer getId()           { return id; }
    public String  getUsername()     { return username; }
    public String  getPasswordHash() { return passwordHash; }
}
