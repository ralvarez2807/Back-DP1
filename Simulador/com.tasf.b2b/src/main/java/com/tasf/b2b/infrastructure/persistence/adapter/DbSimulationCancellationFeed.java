package com.tasf.b2b.infrastructure.persistence.adapter;

import com.tasf.b2b.domain.simulator.feed.CancellationEntry;
import com.tasf.b2b.domain.simulator.feed.CancellationFeed;
import com.tasf.b2b.infrastructure.persistence.entity.simulation.SimulationCancellationEntity;
import com.tasf.b2b.infrastructure.persistence.repository.SimulationCancellationJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.Collections;
import java.util.Iterator;

/**
 * Feed de cancelaciones respaldado por la BD, acotado al rango simulado.
 * Misma estrategia que DbSimulationShipmentFeed: consulta solo [fromUtc, toUtc]
 * en páginas perezosas para no cargar la tabla completa en memoria.
 */
public class DbSimulationCancellationFeed implements CancellationFeed {

    private static final int PAGE_SIZE = 2000;

    private static final Sort ORDER = Sort.by("departureUtc").ascending()
            .and(Sort.by("flightScheduleId").ascending());

    private final SimulationCancellationJpaRepository repo;
    private final Instant fromUtc;
    private final Instant toUtc;

    private Iterator<SimulationCancellationEntity> current = Collections.emptyIterator();
    private int     nextPage        = 0;
    private boolean lastPageReached = false;

    public DbSimulationCancellationFeed(SimulationCancellationJpaRepository repo,
                                        Instant fromUtc,
                                        Instant toUtc) {
        this.repo    = repo;
        this.fromUtc = fromUtc;
        this.toUtc   = toUtc;
    }

    @Override
    public CancellationEntry next() {
        if (!current.hasNext() && !lastPageReached) {
            Slice<SimulationCancellationEntity> page = repo
                    .findByDepartureUtcGreaterThanEqualAndDepartureUtcLessThanEqual(
                            fromUtc, toUtc, PageRequest.of(nextPage++, PAGE_SIZE, ORDER));
            lastPageReached = !page.hasNext();
            current = page.getContent().iterator();
        }
        if (!current.hasNext()) return null;
        SimulationCancellationEntity e = current.next();
        return new CancellationEntry(e.getFlightScheduleId(), e.getDepartureUtc());
    }
}
