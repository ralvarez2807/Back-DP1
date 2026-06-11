package com.tasf.b2b.domain.simulator;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class SimulationClockTest {

    private static final Instant SIM_START = Instant.parse("2026-01-02T00:00:00Z");

    @Test
    void now_en_creacion_es_aproximadamente_simStart() {
        SimulationClock clock = new SimulationClock(SIM_START, 1.0);

        Instant now = clock.now();

        long deltaMs = Duration.between(SIM_START, now).toMillis();
        assertTrue(deltaMs >= 0 && deltaMs < 200,
                "now() debe estar muy cerca de simStart al crear el reloj, delta=" + deltaMs + "ms");
    }

    @Test
    void now_avanza_con_speedFactor() throws InterruptedException {
        double speedFactor = 60.0; // 1 minuto sim por segundo real
        SimulationClock clock = new SimulationClock(SIM_START, speedFactor);

        Thread.sleep(100); // 100ms real → 6 segundos simulados

        Instant now = clock.now();
        long simElapsedMs = Duration.between(SIM_START, now).toMillis();

        // Esperamos entre 4000ms y 10000ms simulados (margen holgado por jitter)
        assertTrue(simElapsedMs >= 4_000 && simElapsedMs <= 10_000,
                "sim elapsed debería ser ~6000ms, fue=" + simElapsedMs + "ms");
    }

    @Test
    void pause_detiene_el_avance_del_reloj() throws InterruptedException {
        SimulationClock clock = new SimulationClock(SIM_START, 1.0);

        clock.pause();
        Instant beforeSleep = clock.now();
        Thread.sleep(200);
        Instant afterSleep = clock.now();
        clock.resume();

        long deltaMs = Duration.between(beforeSleep, afterSleep).toMillis();
        // Durante la pausa el reloj no debe avanzar
        assertTrue(deltaMs < 50,
                "El reloj no debe avanzar durante pausa, delta=" + deltaMs + "ms");
    }

    @Test
    void resume_reanuda_desde_donde_se_pauso() throws InterruptedException {
        SimulationClock clock = new SimulationClock(SIM_START, 1.0);

        Thread.sleep(100);
        Instant antesDepausa = clock.now();

        clock.pause();
        Thread.sleep(200); // esta pausa no debe contar
        clock.resume();

        Thread.sleep(100);
        Instant despues = clock.now();

        // Debe haber avanzado ~100ms sim después del resume, no 300ms
        long delta = Duration.between(antesDepausa, despues).toMillis();
        assertTrue(delta < 300,
                "Después del resume no debe contar el tiempo pausado, delta=" + delta + "ms");
    }

    @Test
    void isPaused_refleja_estado() {
        SimulationClock clock = new SimulationClock(SIM_START, 1.0);

        assertFalse(clock.isPaused());

        clock.pause();
        assertTrue(clock.isPaused());

        clock.resume();
        assertFalse(clock.isPaused());
    }

    @Test
    void pause_doble_no_acumula_tiempo_extra() throws InterruptedException {
        SimulationClock clock = new SimulationClock(SIM_START, 1.0);

        clock.pause();
        clock.pause(); // segunda llamada no debe hacer nada
        Thread.sleep(200);
        clock.resume();

        Instant now = clock.now();
        long deltaMs = Duration.between(SIM_START, now).toMillis();
        // Si se acumulara doble, el reloj iría hacia atrás; no debe pasar
        assertTrue(deltaMs >= 0);
    }

    @Test
    void toWallDeadlineMs_calcula_tiempo_pared_correcto() {
        double speedFactor = 3600.0; // 1 hora sim por segundo real
        SimulationClock clock = new SimulationClock(SIM_START, speedFactor);

        Instant simTarget = SIM_START.plus(Duration.ofHours(1)); // +1h sim
        long wallDeadline = clock.toWallDeadlineMs(simTarget);
        long ahora        = System.currentTimeMillis();

        // El deadline de pared debe ser aproximadamente 1 segundo después de ahora
        long diff = wallDeadline - ahora;
        assertTrue(diff >= 900 && diff <= 1100,
                "Wall deadline debería ser ~1s en el futuro, diff=" + diff + "ms");
    }
}
