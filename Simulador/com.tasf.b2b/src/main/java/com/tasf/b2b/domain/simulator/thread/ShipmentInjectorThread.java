package com.tasf.b2b.domain.simulator.thread;

import com.tasf.b2b.domain.model.graph.immovable.ShipmentDataDTO;
import com.tasf.b2b.domain.simulator.SimulationClock;
import com.tasf.b2b.domain.simulator.SimulationRunner;
import com.tasf.b2b.domain.simulator.event.NewShipmentEvent;
import com.tasf.b2b.domain.simulator.feed.ShipmentFeed;

import java.time.Instant;

/**
 * Hilo productor de envíos.
 *
 * Consume un ShipmentFeed en orden cronológico e inyecta NewShipmentEvent
 * cuando el tiempo de simulación alcanza el entryDateTimeUtc de cada envío.
 *
 * Respeta la pausa del reloj: waitUntilSimTime() usa effectiveWallTimeMs()
 * en lugar de currentTimeMillis().
 */
public class ShipmentInjectorThread implements Runnable {

    private final SimulationRunner runner;
    private final SimulationClock  clock;
    private final ShipmentFeed     feed;
    private final Instant          simStart;
    private final Instant          simEnd;

    public ShipmentInjectorThread(SimulationRunner runner,
                                  SimulationClock clock,
                                  ShipmentFeed feed,
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
        ShipmentDataDTO pending = feed.next();
        try {
            while (pending != null && !Thread.currentThread().isInterrupted()) {
                Instant entryUtc = pending.getEntryDateTimeUtc();

                if (entryUtc.isBefore(simStart)) {
                    pending = feed.next();
                    continue;
                }
                if (entryUtc.isAfter(simEnd)) break;

                waitUntilSimTime(entryUtc);
                runner.submit(new NewShipmentEvent(entryUtc, pending, clock));
                pending = feed.next();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("[INYECTOR] Finalizado.");
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
