package com.tasf.b2b;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada de la aplicación Spring Boot.
 *
 * @SpringBootApplication es un atajo que combina tres anotaciones:
 *   @Configuration     → esta clase puede definir @Bean
 *   @EnableAutoConfiguration → Spring Boot configura automáticamente Tomcat,
 *                              Jackson, Spring Security, etc. según las
 *                              dependencias que encuentre en el classpath.
 *   @ComponentScan     → escanea el paquete com.tasf.b2b y sus subpaquetes
 *                         buscando @Component, @Service, @RestController, etc.
 *
 * SimulationMain sigue existiendo para pruebas manuales rápidas sin Spring.
 * Esta clase es el punto de entrada cuando se quiere levantar la API REST.
 */
@SpringBootApplication
public class
TasfApplication {

    public static void main(String[] args) {
        SpringApplication.run(TasfApplication.class, args);
    }
}
