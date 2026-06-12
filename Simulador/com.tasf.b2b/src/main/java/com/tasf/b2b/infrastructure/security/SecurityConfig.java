package com.tasf.b2b.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuración de Spring Security para la API REST.
 *
 * Decisiones de diseño:
 *   STATELESS: no hay sesión HTTP. Cada request debe traer su JWT.
 *              Esto es el estándar para APIs REST consumidas por frontends.
 *   CSRF disabled: CSRF solo importa cuando el navegador hace requests automáticos
 *                  (formularios HTML). Como usamos JWT en el header, no aplica.
 *   Un usuario hardcodeado: suficiente para la entrega actual.
 *                           Migrar a BD cuando haya múltiples usuarios.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter  jwtAuthFilter;
    private final UserRepository userRepository;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, UserRepository userRepository) {
        this.jwtAuthFilter  = jwtAuthFilter;
        this.userRepository = userRepository;
    }

    /**
     * Cadena de filtros de seguridad: define qué rutas requieren auth y cómo validarla.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // Sin CSRF — no hay cookies de sesión en esta API
                .csrf(AbstractHttpConfigurer::disable)

                // Sin sesión HTTP — cada request es independiente y autenticado por JWT
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Headers de seguridad HTTP
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(ct -> {})
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)
                        )
                )

                // Reglas de autorización por ruta
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        // El handshake WebSocket va autenticado por JwtHandshakeInterceptor (query param token=)
                        // Spring Security no puede leer el JWT del query param, así que se excluye aquí
                        .requestMatchers("/api/v1/simulations/*/ws").permitAll()
                        .anyRequest().authenticated()
                )

                // Sin token o token expirado → 401 (no el 403 por defecto).
                // El frontend distingue así "debes re-autenticarte" (401, intenta
                // refresh o vuelve al login) de un "prohibido" real (403).
                .exceptionHandling(e -> e.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))

                // Insertar el filtro JWT antes del filtro de autenticación por contraseña
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)

                .build();
    }

    /**
     * Carga los usuarios desde UserRepository (users.json por ahora, BD después).
     * BCryptPasswordEncoder.matches() compara el hash almacenado sin re-codificarlo.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            String hash = userRepository.findPasswordHash(username)
                    .orElseThrow(() -> new UsernameNotFoundException(username));
            return User.withUsername(username)
                    .password(hash)
                    .roles("ADMIN")
                    .build();
        };
    }

    /**
     * BCrypt: algoritmo de hasheo de contraseñas con salt automático.
     * Nunca guardar contraseñas en texto plano — BCrypt es el estándar actual.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
