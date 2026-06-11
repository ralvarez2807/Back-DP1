package com.tasf.b2b.infrastructure.persistence.adapter;

import com.tasf.b2b.domain.model.graph.immovable.AirportDataDTO;
import com.tasf.b2b.domain.model.graph.immovable.DeliveryTypeValues;
import com.tasf.b2b.domain.model.graph.immovable.ShipmentDataDTO;
import com.tasf.b2b.domain.simulator.feed.ShipmentFeed;
import com.tasf.b2b.infrastructure.persistence.entity.simulation.SimulationShipmentEntity;
import com.tasf.b2b.infrastructure.persistence.repository.SimulationShipmentJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

/**
 * Feed de envíos respaldado por la BD, acotado al rango simulado.
 *
 * IMPORTANTE: no cargar toda la tabla en memoria. La tabla simulation.shipments
 * puede tener millones de filas (meses de datos oficiales); un findAll() aquí
 * agota el heap de la JVM antes de que la sesión arranque (OutOfMemoryError en
 * el POST /simulations). Por eso se consulta solo [fromUtc, toUtc] y en páginas
 * perezosas: la siguiente página se trae recién cuando el inyector consumió la
 * anterior, manteniendo en memoria PAGE_SIZE entidades como máximo.
 */
public class DbSimulationShipmentFeed implements ShipmentFeed {

    private static final int PAGE_SIZE = 2000;

    // Orden estable (entryUtc + id) para que la paginación por offset no salte
    // ni repita filas cuando varios envíos comparten el mismo entryUtc.
    private static final Sort ORDER = Sort.by("entryUtc").ascending()
            .and(Sort.by("shipmentId.id").ascending());

    private final SimulationShipmentJpaRepository repo;
    private final Map<String, AirportDataDTO>     airports;
    private final DeliveryTypeValues              deliveryTypes;
    private final Instant                         fromUtc;
    private final Instant                         toUtc;

    private Iterator<SimulationShipmentEntity> current = Collections.emptyIterator();
    private int     nextPage        = 0;
    private boolean lastPageReached = false;

    public DbSimulationShipmentFeed(SimulationShipmentJpaRepository repo,
                                    Map<String, AirportDataDTO> airports,
                                    DeliveryTypeValues deliveryTypes,
                                    Instant fromUtc,
                                    Instant toUtc) {
        this.repo          = repo;
        this.airports      = airports;
        this.deliveryTypes = deliveryTypes;
        this.fromUtc       = fromUtc;
        this.toUtc         = toUtc;
    }

    @Override
    public ShipmentDataDTO next() {
        if (!current.hasNext() && !lastPageReached) {
            Slice<SimulationShipmentEntity> page = repo
                    .findByEntryUtcGreaterThanEqualAndEntryUtcLessThanEqual(
                            fromUtc, toUtc, PageRequest.of(nextPage++, PAGE_SIZE, ORDER));
            lastPageReached = !page.hasNext();
            current = page.getContent().iterator();
        }
        return current.hasNext() ? toDto(current.next()) : null;
    }

    private ShipmentDataDTO toDto(SimulationShipmentEntity e) {
        AirportDataDTO origin = airports.get(e.getOriginIcao());
        AirportDataDTO dest   = airports.get(e.getDestinationIcao());
        return new ShipmentDataDTO(
                e.getId(),
                e.getEntryUtc(),
                origin,
                dest,
                e.getQuantity(),
                e.getClientId(),
                deliveryTypes
        );
    }
}
