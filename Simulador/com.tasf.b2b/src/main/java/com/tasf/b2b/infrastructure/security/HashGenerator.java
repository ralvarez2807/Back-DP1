package com.tasf.b2b.infrastructure.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Utilidad de un solo uso para generar hashes BCrypt.
 * Correr desde IntelliJ, copiar el hash resultante e insertarlo en la BD.
 * No es un @Component — no se activa con Spring Boot.
 */
public class HashGenerator {

    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

        // Cambia esta contraseña por la tuya antes de correrlo
        String rawPassword = "admin1234";

        String hash = encoder.encode(rawPassword);
        System.out.println("Hash BCrypt:");
        System.out.println(hash);
        System.out.println();
        System.out.println("SQL para insertar:");
        System.out.println("INSERT INTO public.users (username, password_hash) VALUES ('admin', '" + hash + "');");
    }
}
