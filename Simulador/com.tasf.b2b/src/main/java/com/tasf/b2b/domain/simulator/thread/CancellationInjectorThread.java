package com.tasf.b2b.domain.simulator.thread;

import com.tasf.b2b.domain.simulator.SimulationClock;
import com.tasf.b2b.domain.simulator.SimulationRunner;
import com.tasf.b2b.domain.simulator.event.FlightCancelledEvent;
import com.tasf.b2b.domain.simulator.feed.CancellationEntry;
import com.tasf.b2b.domain.simulator.feed.CancellationFeed;

import java.time.Instant;

/**
 * Hilo productor de cancelaciones.
 *
 * Consume un CancellationFeed en orden cronológico e inyecta FlightCancelledEvent
 * en el instante exacto de la salida del vuelo. Descarta cancelaciones de
 * vuelos que ya partieron al momento de procesarlos.
 */
public class CancellationInjectorThread implements Runnable {

    private final SimulationRunner  runner;
    private final SimulationClock   clock;
    private final CancellationFeed  feed;
    private final Instant           simStart;
    private final Instant           simEnd;

    public CancellationInjectorThread(SimulationRunner runner,
                                      SimulationClock clock,
                                      CancellationFeed feed,
                                      Instant simStart,
                                      Instant simEnd) {
        this.runner   = runner;
        this.clock    = clock;
        this.feed     = feed;
        this.simStart = simStart;
        this.simEnd   = simEnd;
    }

    @Override
    public void run() {
        try {
            CancellationEntry c;
            while ((c = feed.next()) != null && !Thread.currentThread().isInterrupted()) {
                Instant notifyAt = c.depTimeUtc();

                if (notifyAt.isBefore(simStart)) notifyAt = simStart;
                if (notifyAt.isAfter(simEnd))    continue;

                waitUntilSimTime(notifyAt);

                // Descartar si el vuelo ya partió al momento de procesar
                if (!c.depTimeUtc().isAfter(clock.now())) continue;

                runner.submit(new FlightCancelledEvent(notifyAt, c.flightKey(), c.depTimeUtc(), clock));
                System.out.printf("[CANCELACIONES] Cancelación inyectada: %s (salida %s)%n",
                        c.flightKey(), c.depTimeUtc());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("[CANCELACIONES] Finalizado.");
    }

    /**
     * Bloquea hasta que el tiempo de simulación efectivo alcance targetSimTime.
     * Duerme en intervalos cortos para ser interruptible y responder a la pausa.
     */
    private void waitUntilSimTime(Instant targetSimTime) throws InterruptedException {
        while (true) {
            long remaining = clock.toWallDeadlineMs(targetSimTime) - clock.effectiveWallTimeMs();
            if (remaining <= 0) return;
            Thread.sleep(Math.min(Math.max(1L, remaining / 2), 50L));
        }
    }
}
