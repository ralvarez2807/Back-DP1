package com.tasf.b2b.domain.simulator;

import java.time.Duration;
import java.time.Instant;

public class SimulationClock {
    private final Instant simStart;
    private final long    wallStartMs;
    private final double  speedFactor;

    // -1 = corriendo; >= 0 = epoch ms en que se pausó
    // es volatile para que ningún hilo guarde una copia local que puede ser antigua
    private volatile long pauseStartMs  = -1;
    private volatile long totalPausedMs = 0;

    public SimulationClock(Instant simStart, double speedFactor) {
        this.simStart     = simStart;
        this.wallStartMs  = System.currentTimeMillis();
        this.speedFactor  = speedFactor;
    }

    /** Tiempo de simulación actual derivado del tiempo real efectivo transcurrido. */
    public Instant now() {
        long effectiveElapsedMs = effectiveWallTimeMs() - wallStartMs;
        long simElapsedMs       = (long) (effectiveElapsedMs * speedFactor);
        return simStart.plusMillis(simElapsedMs);
    }

    /**
     * Momento de pared efectivo en que debe dispararse simTime.
     * wallStart + (simTime − simStart) / speedFactor.
     * El tiempo efectivo se congela en pausa, por lo que los eventos
     * dejan de cumplir su deadline mientras el reloj está pausado.
     */
    public long toWallDeadlineMs(Instant simTime) {
        long simOffsetMs  = Duration.between(simStart, simTime).toMillis();
        long wallOffsetMs = (long) (simOffsetMs / speedFactor);
        return wallStartMs + wallOffsetMs;
    }

    /**
     * Tiempo de pared efectivo: System.currentTimeMillis() menos el total
     * de tiempo acumulado en pausa. Se detiene durante la pausa.
     */
    public synchronized long effectiveWallTimeMs() {
        long now    = System.currentTimeMillis();
        long paused = totalPausedMs;
        if (pauseStartMs >= 0) paused += now - pauseStartMs;
        return now - paused;
    }

    public synchronized void pause() {
        if (pauseStartMs < 0) {
            pauseStartMs = System.currentTimeMillis();
        }
    }

    public synchronized void resume() {
        if (pauseStartMs >= 0) {
            totalPausedMs += System.currentTimeMillis() - pauseStartMs;
            pauseStartMs = -1;
        }
    }

    public synchronized boolean isPaused() { return pauseStartMs >= 0; }

    public Instant getSimStart()    { return simStart; }
    public double  getSpeedFactor() { return speedFactor; }
}
