package com.tasf.b2b.infrastructure.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Utilidad de un solo uso para generar hashes BCrypt(SHA-256(password)).
 * El front hashea con SHA-256 antes de enviar; el back almacena bcrypt de ese SHA-256.
 * Correr desde IntelliJ, copiar el hash resultante e insertarlo en la BD.
 * No es un @Component — no se activa con Spring Boot.
 */
public class HashGenerator {

    public static void main(String[] args) throws Exception {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

        // Cambia estos valores antes de correrlo
        String username    = "admin";
        String rawPassword = "admin123";

        String sha256hex = sha256(rawPassword);
        String hash      = encoder.encode(sha256hex);

        System.out.println("SHA-256 de la contraseña (lo que envía el front):");
        System.out.println(sha256hex);
        System.out.println();
        System.out.println("Hash BCrypt(SHA-256) almacenado en BD:");
        System.out.println(hash);
        System.out.println();
        System.out.println("SQL para insertar / actualizar:");
        System.out.println("INSERT INTO public.users (username, password_hash) VALUES ('" + username + "', '" + hash + "')");
        System.out.println("ON CONFLICT (username) DO UPDATE SET password_hash = EXCLUDED.password_hash;");
    }

    private static String sha256(String input) throws Exception {
        byte[] bytes = MessageDigest.getInstance("SHA-256").digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(bytes);
    }
}
