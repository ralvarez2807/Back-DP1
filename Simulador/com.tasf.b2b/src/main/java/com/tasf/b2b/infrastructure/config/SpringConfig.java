package com.tasf.b2b.infrastructure.config;

import com.tasf.b2b.application.port.in.AvailableDaysPort;
import com.tasf.b2b.application.usecase.QuerySimulationUseCase;
import com.tasf.b2b.application.usecase.RunSimulationUseCase;
import com.tasf.b2b.application.usecase.SimulationRegistry;
import com.tasf.b2b.domain.model.graph.immovable.AirportDataDTO;
import com.tasf.b2b.domain.model.graph.immovable.DeliveryTypeValues;
import com.tasf.b2b.domain.model.graph.immovable.FlightScheduleDataDTO;
import com.tasf.b2b.infrastructure.persistence.adapter.DbAvailableDaysService;
import com.tasf.b2b.infrastructure.persistence.adapter.DbSimulationCancellationFeed;
import com.tasf.b2b.infrastructure.persistence.adapter.DbSimulationShipmentFeed;
import com.tasf.b2b.infrastructure.persistence.entity.reference.AirportEntity;
import com.tasf.b2b.infrastructure.persistence.entity.reference.FlightScheduleEntity;
import com.tasf.b2b.infrastructure.persistence.repository.AirportJpaRepository;
import com.tasf.b2b.infrastructure.persistence.repository.FlightScheduleJpaRepository;
import com.tasf.b2b.infrastructure.persistence.repository.SimulationCancellationJpaRepository;
import com.tasf.b2b.infrastructure.persistence.repository.SimulationShipmentJpaRepository;
import com.tasf.b2b.presentation.websocket.InMemoryStatePublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
public class SpringConfig {

    @Bean
    public SimulationRegistry simulationRegistry() {
        return new SimulationRegistry();
    }

    @Bean
    public RunSimulationUseCase runSimulationUseCase(
            SimulationRegistry registry,
            AirportJpaRepository airportRepo,
            FlightScheduleJpaRepository flightRepo,
            SimulationShipmentJpaRepository shipmentRepo,
            SimulationCancellationJpaRepository cancellationRepo) {

        DeliveryTypeValues deliveryTypes = new DeliveryTypeValues();

        Map<String, AirportDataDTO> airports = airportRepo.findAll().stream()
                .collect(Collectors.toMap(AirportEntity::getIcao, this::toAirportDto));

        List<FlightScheduleDataDTO> flights = flightRepo.findAll().stream()
                .map(e -> toFlightScheduleDto(e, airports))
                .collect(Collectors.toList());

        return new RunSimulationUseCase(
                registry,
                airports,
                flights,
                (from, to) -> new DbSimulationShipmentFeed(shipmentRepo, airports, deliveryTypes, from, to),
                (from, to) -> new DbSimulationCancellationFeed(cancellationRepo, from, to),
                InMemoryStatePublisher::new
        );
    }

    @Bean
    public QuerySimulationUseCase querySimulationUseCase(SimulationRegistry registry) {
        return new QuerySimulationUseCase(registry);
    }

    @Bean
    public AvailableDaysPort availableDaysPort(SimulationShipmentJpaRepository shipmentRepo) {
        return new DbAvailableDaysService(shipmentRepo);
    }

    // ── Conversiones entity → domain DTO ─────────────────────────────────────

    private AirportDataDTO toAirportDto(AirportEntity e) {
        return new AirportDataDTO(
                e.getIcao(), e.getCity(), e.getCountry(), e.getContinent(),
                e.getShortName(), e.getGmtOffset(), e.getCapacity(),
                e.getLatitude(), e.getLongitude()
        );
    }

    private FlightScheduleDataDTO toFlightScheduleDto(FlightScheduleEntity e,
                                                       Map<String, AirportDataDTO> airports) {
        return new FlightScheduleDataDTO(
                airports.get(e.getOriginIcao()),
                airports.get(e.getDestinationIcao()),
                e.getDepartureLocal(),
                e.getArrivalLocal(),
                e.getCapacity()
        );
    }
}
