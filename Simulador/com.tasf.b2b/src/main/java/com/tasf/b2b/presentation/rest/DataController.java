package com.tasf.b2b.presentation.rest;

import com.tasf.b2b.application.port.in.AirportAdminPort;
import com.tasf.b2b.application.port.in.AvailableDaysPort;
import com.tasf.b2b.application.usecase.RunSimulationUseCase;
import com.tasf.b2b.domain.model.graph.immovable.AirportDataDTO;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/v1/data")
public class DataController {

    private final AvailableDaysPort    availableDaysPort;
    private final RunSimulationUseCase runSimulationUseCase;
    private final AirportAdminPort     airportAdminPort;

    public DataController(AvailableDaysPort availableDaysPort,
                          RunSimulationUseCase runSimulationUseCase,
                          AirportAdminPort airportAdminPort) {
        this.availableDaysPort    = availableDaysPort;
        this.runSimulationUseCase = runSimulationUseCase;
        this.airportAdminPort     = airportAdminPort;
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    record AvailableDaysResponse(List<LocalDate> availableDates) {}

    record AirportResponse(String icao, String city, String country, String continent,
                           String shortName, int gmtOffset, int capacity,
                           double lat, double lon) {

        static AirportResponse from(AirportDataDTO a) {
            return new AirportResponse(
                    a.getIcao(), a.getCity(), a.getCountry(), a.getContinent(),
                    a.getShort_name(), a.getGmtOffset(), a.getCapacity(),
                    a.getLatitude(), a.getLongitude());
        }
    }

    record UpdateCapacityRequest(Integer capacity) {}

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
                .map(AirportResponse::from)
                .sorted(Comparator.comparing(AirportResponse::icao))
                .toList();
    }

    /**
     * Modifica la capacidad de almacén de un aeropuerto existente. No se crean ni
     * eliminan aeropuertos: la red es fija. El cambio se persiste y se refleja en vivo.
     */
    @PutMapping("/airports/{icao}/capacity")
    public AirportResponse updateAirportCapacity(@PathVariable String icao,
                                                 @RequestBody UpdateCapacityRequest req) {
        if (req.capacity() == null || req.capacity() <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "capacity debe ser un entero mayor que 0");

        String key = icao.trim().toUpperCase();
        airportAdminPort.updateCapacity(key, req.capacity());

        AirportDataDTO updated = runSimulationUseCase.getAirports().get(key);
        if (updated == null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Aeropuerto no encontrado: " + icao);
        return AirportResponse.from(updated);
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
