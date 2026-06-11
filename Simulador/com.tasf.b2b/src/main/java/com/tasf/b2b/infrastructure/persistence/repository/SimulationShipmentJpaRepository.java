package com.tasf.b2b.infrastructure.persistence.repository;

import com.tasf.b2b.infrastructure.persistence.entity.base.ShipmentId;
import com.tasf.b2b.infrastructure.persistence.entity.simulation.SimulationShipmentEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface SimulationShipmentJpaRepository extends JpaRepository<SimulationShipmentEntity, ShipmentId> {

    /**
     * Envíos cuyo entryUtc cae en [from, to], paginados.
     * Usado por DbSimulationShipmentFeed — NO usar findAll(): la tabla completa
     * no cabe en memoria cuando hay meses de datos cargados.
     */
    Slice<SimulationShipmentEntity> findByEntryUtcGreaterThanEqualAndEntryUtcLessThanEqual(
            Instant from, Instant to, Pageable pageable);

    @Query("SELECT DISTINCT CAST(s.entryUtc AS LocalDate) FROM SimulationShipmentEntity s ORDER BY 1")
    List<LocalDate> findDistinctDates();

    @Query("SELECT s.shipmentId.originIcao, COUNT(s) FROM SimulationShipmentEntity s GROUP BY s.shipmentId.originIcao ORDER BY s.shipmentId.originIcao")
    List<Object[]> countByOriginIcao();

    @Query("SELECT CAST(s.entryUtc AS LocalDate) AS date, COUNT(s) FROM SimulationShipmentEntity s " +
           "WHERE s.shipmentId.originIcao = :icao GROUP BY CAST(s.entryUtc AS LocalDate) ORDER BY 1")
    List<Object[]> countByDateForOrigin(@Param("icao") String icao);

    @Modifying
    @Query("DELETE FROM SimulationShipmentEntity s WHERE s.shipmentId.originIcao = :icao")
    void deleteByOriginIcao(@Param("icao") String icao);
}
