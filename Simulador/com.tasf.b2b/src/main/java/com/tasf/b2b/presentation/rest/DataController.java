package com.tasf.b2b.presentation.rest;

import com.tasf.b2b.application.port.in.AvailableDaysPort;
import com.tasf.b2b.application.usecase.RunSimulationUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/v1/data")
public class DataController {

    private final AvailableDaysPort    availableDaysPort;
    private final RunSimulationUseCase runSimulationUseCase;

    public DataController(AvailableDaysPort availableDaysPort, RunSimulationUseCase runSimulationUseCase) {
        this.availableDaysPort    = availableDaysPort;
        this.runSimulationUseCase = runSimulationUseCase;
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    record AvailableDaysResponse(List<LocalDate> availableDates) {}

    record AirportResponse(String icao, String city, String continent,
                           double lat, double lon, int capacity) {}

    record RouteResponse(String id, String originIcao, String destIcao,
                         int capacity, String depTimeLocal, String arrTimeLocal) {}

    // ── Endpoints ─────────────────────────────────────────────────────────────

    @GetMapping("/available-days")
    public AvailableDaysResponse getAvailableDays() {
        return new AvailableDaysResponse(availableDaysPort.getAvailableDates());
    }

    @GetMapping("/airports")
    public List<AirportResponse> getAirports() {
        return runSimulationUseCase.getAirports().values().stream()
                .map(a -> new AirportResponse(
                        a.getIcao(), a.getCity(), a.getContinent(),
                        a.getLatitude(), a.getLongitude(), a.getCapacity()))
                .sorted(Comparator.comparing(AirportResponse::icao))
                .toList();
    }

    @GetMapping("/routes")
    public List<RouteResponse> getRoutes() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm");
        return runSimulationUseCase.getFlights().stream()
                .map(f -> new RouteResponse(
                        f.getId(),
                        f.getOriginAirport().getIcao(),
                        f.getDestAirport().getIcao(),
                        f.getCapacity(),
                        f.getDepartureTimeLocal().format(fmt),
                        f.getArrivalTimeLocal().format(fmt)))
                .toList();
    }
}
