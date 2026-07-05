package com.tasf.b2b.presentation.rest;

import com.tasf.b2b.application.port.in.AirportAdminPort;
import com.tasf.b2b.application.port.in.FlightAdminPort;
import com.tasf.b2b.domain.model.graph.immovable.AirportDataDTO;
import com.tasf.b2b.domain.model.graph.immovable.FlightScheduleDataDTO;
import com.tasf.b2b.infrastructure.persistence.adapter.DataLoadService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Carga de datos de referencia e históricos desde archivos TXT, y alta/edición
 * individual de aeropuertos y vuelos (exigencias v2.0: LE-10, LE-12, LE-13, LE-14, LE-15).
 *
 * POST   /api/v1/admin/airports?mode=replace|merge → sube aeropuertos (archivo)
 * POST   /api/v1/admin/airports/single              → crea un aeropuerto individual
 * POST   /api/v1/admin/flights?mode=replace|merge   → sube vuelos programados (archivo)
 * POST   /api/v1/admin/flights/single               → crea un vuelo individual
 * PUT    /api/v1/admin/flights/:scheduleId          → modifica horario/capacidad de un vuelo
 * POST   /api/v1/admin/shipments                    → carga histórica (solo si tabla vacía)
 * DELETE /api/v1/admin/shipments                    → borra todos los shipments
 */
@RestController
@RequestMapping("/api/v1/admin")
public class DataLoadController {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final DataLoadService  service;
    private final AirportAdminPort airportAdminPort;
    private final FlightAdminPort  flightAdminPort;

    public DataLoadController(DataLoadService service, AirportAdminPort airportAdminPort,
                              FlightAdminPort flightAdminPort) {
        this.service          = service;
        this.airportAdminPort = airportAdminPort;
        this.flightAdminPort  = flightAdminPort;
    }

    record LoadResponse(String message, int count, List<String> errors, List<String> warnings) {}

    // ── Aeropuerto individual (LE-10, LE-13, LE-14, LE-15) ────────────────────

    record CreateAirportRequest(String icao, String city, String country, String continent,
                                String shortName, Integer gmtOffset, Integer capacity,
                                Double lat, Double lon) {}

    record AirportResponse(String icao, String city, String country, String continent,
                           String shortName, int gmtOffset, int capacity,
                           double lat, double lon) {
        static AirportResponse from(AirportDataDTO a) {
            return new AirportResponse(a.getIcao(), a.getCity(), a.getCountry(), a.getContinent(),
                    a.getShort_name(), a.getGmtOffset(), a.getCapacity(), a.getLatitude(), a.getLongitude());
        }
    }

    @PostMapping("/airports/single")
    @ResponseStatus(HttpStatus.CREATED)
    public AirportResponse createAirport(@RequestBody CreateAirportRequest req) {
        if (req.icao() == null || req.icao().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campo requerido: icao");
        if (req.city() == null || req.city().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campo requerido: city");
        if (req.continent() == null || req.continent().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campo requerido: continent");
        if (req.gmtOffset() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campo requerido: gmtOffset");
        if (req.capacity() == null || req.capacity() <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "capacity debe ser un entero mayor que 0");
        if (req.lat() == null || req.lon() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campos requeridos: lat, lon");

        AirportDataDTO created = airportAdminPort.createAirport(
                req.icao(), req.city(), req.country(), req.continent(), req.shortName(),
                req.gmtOffset(), req.capacity(), req.lat(), req.lon());
        return AirportResponse.from(created);
    }

    // ── Vuelo individual (LE-10, LE-12) ───────────────────────────────────────

    record CreateFlightRequest(String originIcao, String destIcao,
                               String depTimeLocal, String arrTimeLocal, Integer capacity) {}

    record UpdateFlightRequest(String depTimeLocal, String arrTimeLocal, Integer capacity) {}

    record RouteResponse(String id, String originIcao, String destIcao,
                         int capacity, String depTimeLocal, String arrTimeLocal) {
        static RouteResponse from(FlightScheduleDataDTO f) {
            return new RouteResponse(f.getId(), f.getOriginAirport().getIcao(), f.getDestAirport().getIcao(),
                    f.getCapacity(), f.getDepartureTimeLocal().format(HHMM), f.getArrivalTimeLocal().format(HHMM));
        }
    }

    @PostMapping("/flights/single")
    @ResponseStatus(HttpStatus.CREATED)
    public RouteResponse createFlight(@RequestBody CreateFlightRequest req) {
        if (req.originIcao() == null || req.originIcao().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campo requerido: originIcao");
        if (req.destIcao() == null || req.destIcao().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campo requerido: destIcao");
        if (req.depTimeLocal() == null || req.arrTimeLocal() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campos requeridos: depTimeLocal, arrTimeLocal");
        if (req.capacity() == null || req.capacity() <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "capacity debe ser un entero mayor que 0");

        LocalTime dep = parseLocalTime(req.depTimeLocal());
        LocalTime arr = parseLocalTime(req.arrTimeLocal());

        FlightScheduleDataDTO created = flightAdminPort.createFlight(
                req.originIcao(), req.destIcao(), dep, arr, req.capacity());
        return RouteResponse.from(created);
    }

    @PutMapping("/flights/{scheduleId}")
    public RouteResponse updateFlight(@PathVariable String scheduleId, @RequestBody UpdateFlightRequest req) {
        if (req.depTimeLocal() == null && req.arrTimeLocal() == null && req.capacity() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Debe enviar al menos un campo: depTimeLocal, arrTimeLocal o capacity");
        if (req.capacity() != null && req.capacity() <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "capacity debe ser un entero mayor que 0");

        LocalTime dep = req.depTimeLocal() != null ? parseLocalTime(req.depTimeLocal()) : null;
        LocalTime arr = req.arrTimeLocal() != null ? parseLocalTime(req.arrTimeLocal()) : null;

        FlightScheduleDataDTO updated = flightAdminPort.updateFlight(scheduleId, dep, arr, req.capacity());
        return RouteResponse.from(updated);
    }

    private static LocalTime parseLocalTime(String value) {
        try {
            return LocalTime.parse(value, HHMM);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hora inválida (se esperaba HH:mm): " + value);
        }
    }

    // ── Aeropuertos ───────────────────────────────────────────────────────────

    @PostMapping(value = "/airports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public LoadResponse uploadAirports(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "mode", defaultValue = "merge") String mode) {
        try {
            boolean replace = "replace".equalsIgnoreCase(mode);
            DataLoadService.LoadResult result = service.loadAirports(file, replace);
            String msg = replace ? "Aeropuertos reemplazados" : "Aeropuertos actualizados (merge)";
            return new LoadResponse(msg, result.count(), result.errors(), result.warnings());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    // ── Vuelos programados ────────────────────────────────────────────────────

    @PostMapping(value = "/flights", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public LoadResponse uploadFlights(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "mode", defaultValue = "merge") String mode) {
        try {
            boolean replace = "replace".equalsIgnoreCase(mode);
            DataLoadService.LoadResult result = service.loadFlights(file, replace);
            String msg = replace ? "Vuelos reemplazados" : "Vuelos actualizados (merge)";
            return new LoadResponse(msg, result.count(), result.errors(), result.warnings());
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    // ── Shipments históricos ──────────────────────────────────────────────────

    @PostMapping(value = "/shipments/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public LoadResponse uploadShipmentFile(
            @RequestParam("file") MultipartFile file) {
        try {
            DataLoadService.LoadResult result = service.loadSingleShipmentFile(file);
            return new LoadResponse(
                    "Shipments cargados desde " + file.getOriginalFilename(),
                    result.count(), result.errors(), result.warnings());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @PostMapping(value = "/shipments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public LoadResponse uploadShipments(
            @RequestParam("files") List<MultipartFile> files) {
        try {
            DataLoadService.LoadResult result = service.loadShipments(files);
            return new LoadResponse(
                    "Shipments cargados y ordenados por entry_utc",
                    result.count(), result.errors(), result.warnings());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @DeleteMapping("/shipments")
    public LoadResponse deleteShipments() {
        int deleted = service.deleteShipments();
        return new LoadResponse("Shipments eliminados", deleted, List.of(), List.of());
    }
}
