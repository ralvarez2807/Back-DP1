package com.tasf.b2b.infrastructure.persistence.adapter;

import com.tasf.b2b.domain.model.graph.immovable.AirportDataDTO;
import com.tasf.b2b.domain.model.graph.immovable.DeliveryTypeValues;
import com.tasf.b2b.domain.model.graph.immovable.FlightScheduleDataDTO;
import com.tasf.b2b.domain.model.graph.immovable.ShipmentDataDTO;
import com.tasf.b2b.infrastructure.files.AirportParser;
import com.tasf.b2b.infrastructure.files.FlightParser;
import com.tasf.b2b.infrastructure.files.ShipmentParser;
import com.tasf.b2b.infrastructure.persistence.entity.reference.AirportEntity;
import com.tasf.b2b.infrastructure.persistence.entity.reference.FlightScheduleEntity;
import com.tasf.b2b.infrastructure.persistence.entity.simulation.SimulationShipmentEntity;
import com.tasf.b2b.infrastructure.persistence.repository.AirportJpaRepository;
import com.tasf.b2b.infrastructure.persistence.repository.FlightScheduleJpaRepository;
import com.tasf.b2b.infrastructure.persistence.repository.SimulationShipmentJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class DataLoadService {

    private static final int    BATCH_SIZE    = 500;
    private static final Pattern ICAO_PATTERN = Pattern.compile("_envios_([A-Z]{4})_");

    private final AirportJpaRepository            airportRepo;
    private final FlightScheduleJpaRepository     flightRepo;
    private final SimulationShipmentJpaRepository shipmentRepo;

    @PersistenceContext
    private EntityManager em;

    public DataLoadService(AirportJpaRepository airportRepo,
                           FlightScheduleJpaRepository flightRepo,
                           SimulationShipmentJpaRepository shipmentRepo) {
        this.airportRepo  = airportRepo;
        this.flightRepo   = flightRepo;
        this.shipmentRepo = shipmentRepo;
    }

    // ── Resultado común ───────────────────────────────────────────────────────

    public record LoadResult(int count, List<String> errors, List<String> warnings) {}

    // ── Aeropuertos ───────────────────────────────────────────────────────────

    @Transactional
    public LoadResult loadAirports(MultipartFile file, boolean replace) throws IOException {
        Map<String, AirportDataDTO> parsed = AirportParser.parse(file.getInputStream());
        if (parsed.isEmpty()) throw new IllegalArgumentException("El archivo no contiene aeropuertos válidos.");

        List<AirportEntity> entities = parsed.values().stream()
                .map(this::toAirportEntity)
                .toList();

        List<String> warnings = new ArrayList<>();
        if (replace) {
            long flightCount = flightRepo.count();
            if (flightCount > 0) {
                flightRepo.deleteAll();
                warnings.add("Se eliminaron " + flightCount + " vuelos programados (dependen de aeropuertos). Vuelve a subirlos.");
            }
            airportRepo.deleteAll();
        }

        airportRepo.saveAll(entities);
        return new LoadResult(entities.size(), List.of(), warnings);
    }

    // ── Vuelos ────────────────────────────────────────────────────────────────

    @Transactional
    public LoadResult loadFlights(MultipartFile file, boolean replace) throws IOException {
        Map<String, AirportDataDTO> airports = loadAirportsFromDb();
        if (airports.isEmpty()) throw new IllegalStateException("No hay aeropuertos en la BD. Sube aeropuertos primero.");

        List<FlightScheduleDataDTO> parsed = FlightParser.parse(file.getInputStream(), airports);
        if (parsed.isEmpty()) throw new IllegalArgumentException("El archivo no contiene vuelos válidos.");

        List<FlightScheduleEntity> entities = parsed.stream()
                .map(this::toFlightEntity)
                .toList();

        if (replace) flightRepo.deleteAll();

        flightRepo.saveAll(entities);
        return new LoadResult(entities.size(), List.of(), List.of());
    }

    // ── Shipments ─────────────────────────────────────────────────────────────

    @Transactional
    public LoadResult loadSingleShipmentFile(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        Matcher m = ICAO_PATTERN.matcher(filename);
        if (!m.find())
            throw new IllegalArgumentException(
                    "Nombre de archivo inválido (se esperaba _envios_ICAO_.txt): " + filename);

        Map<String, AirportDataDTO> airports = loadAirportsFromDb();

        String icao = m.group(1);
        AirportDataDTO origin = airports.get(icao);
        if (origin == null)
            throw new IllegalArgumentException(
                    "Aeropuerto origen no registrado en BD: " + icao);

        DeliveryTypeValues deliveryTypes = new DeliveryTypeValues();
        List<SimulationShipmentEntity> entities = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.ISO_8859_1))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty() || line.trim().startsWith("//")) continue;

                String[] parts = line.trim().split("-");
                if (parts.length == 7) {
                    String destIcao = parts[4];
                    if (!airports.containsKey(destIcao)) {
                        errors.add("Aeropuerto destino no registrado: " + destIcao + " (línea: " + line.trim() + ")");
                        continue;
                    }
                }

                ShipmentDataDTO dto = ShipmentParser.parseLine(line, origin, airports, deliveryTypes);
                if (dto != null) {
                    entities.add(new SimulationShipmentEntity(
                            dto.getId(),
                            dto.getOriginAirport().getIcao(),
                            dto.getDestAirport().getIcao(),
                            dto.getEntryDateTimeUtc(),
                            (short) dto.getQuantity(),
                            dto.getClientId()));
                }
            }
        }

        for (int i = 0; i < entities.size(); i += BATCH_SIZE) {
            shipmentRepo.saveAll(entities.subList(i, Math.min(i + BATCH_SIZE, entities.size())));
            em.flush();
            em.clear();
        }

        return new LoadResult(entities.size(), errors, List.of());
    }

    @Transactional
    public LoadResult loadShipments(List<MultipartFile> files) throws IOException {
        if (shipmentRepo.count() > 0)
            throw new IllegalStateException("Ya hay shipments en la BD. Usa DELETE /api/v1/admin/shipments primero.");

        Map<String, AirportDataDTO> airports = loadAirportsFromDb();
        if (airports.isEmpty()) throw new IllegalStateException("No hay aeropuertos en la BD. Sube aeropuertos primero.");

        DeliveryTypeValues deliveryTypes = new DeliveryTypeValues();
        List<ShipmentDataDTO> all  = new ArrayList<>();
        List<String>          errors = new ArrayList<>();

        for (MultipartFile file : files) {
            String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
            Matcher m = ICAO_PATTERN.matcher(filename);
            if (!m.find()) {
                errors.add("Nombre de archivo inválido (se esperaba _envios_ICAO_.txt): " + filename);
                continue;
            }
            AirportDataDTO origin = airports.get(m.group(1));
            if (origin == null) {
                errors.add("Aeropuerto no registrado en BD: " + m.group(1) + " (" + filename + ")");
                continue;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(file.getInputStream(), StandardCharsets.ISO_8859_1))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    ShipmentDataDTO dto = ShipmentParser.parseLine(line, origin, airports, deliveryTypes);
                    if (dto != null) all.add(dto);
                }
            }
        }

        // Ordenar por entry_utc antes de guardar
        all.sort(Comparator.comparing(ShipmentDataDTO::getEntryDateTimeUtc));

        // Guardar en lotes
        List<SimulationShipmentEntity> entities = all.stream()
                .map(dto -> new SimulationShipmentEntity(
                        dto.getId(),
                        dto.getOriginAirport().getIcao(),
                        dto.getDestAirport().getIcao(),
                        dto.getEntryDateTimeUtc(),
                        (short) dto.getQuantity(),
                        dto.getClientId()))
                .toList();

        for (int i = 0; i < entities.size(); i += BATCH_SIZE) {
            shipmentRepo.saveAll(entities.subList(i, Math.min(i + BATCH_SIZE, entities.size())));
            em.flush();
            em.clear();
        }

        return new LoadResult(entities.size(), errors, List.of());
    }

    @Transactional
    public int deleteShipments() {
        long count = shipmentRepo.count();
        shipmentRepo.deleteAll();
        return (int) count;
    }

    // ── Históricos — consultas ────────────────────────────────────────────────

    public List<AirportShipmentStatus> getHistoricalStatus() {
        return shipmentRepo.countByOriginIcao().stream()
                .map(row -> new AirportShipmentStatus((String) row[0], ((Number) row[1]).intValue()))
                .toList();
    }

    public AirportShipmentDetail getHistoricalDetail(String icao) {
        if (!airportRepo.existsById(icao))
            throw new IllegalArgumentException("Aeropuerto no registrado en BD: " + icao);

        List<DailyCount> daily = shipmentRepo.countByDateForOrigin(icao).stream()
                .map(row -> new DailyCount(row[0].toString(), ((Number) row[1]).intValue()))
                .toList();

        int total = daily.stream().mapToInt(DailyCount::count).sum();
        return new AirportShipmentDetail(icao, total, daily);
    }

    // ── Históricos — carga por aeropuerto ─────────────────────────────────────

    @Transactional
    public LoadResult loadHistoricalFile(MultipartFile file, boolean replace) throws IOException {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        Matcher m = ICAO_PATTERN.matcher(filename);
        if (!m.find())
            throw new IllegalArgumentException(
                    "Nombre de archivo inválido (se esperaba _envios_ICAO_.txt): " + filename);

        Map<String, AirportDataDTO> airports = loadAirportsFromDb();

        String icao = m.group(1);
        AirportDataDTO origin = airports.get(icao);
        if (origin == null)
            throw new IllegalArgumentException("Aeropuerto origen no registrado en BD: " + icao);

        DeliveryTypeValues deliveryTypes = new DeliveryTypeValues();
        List<SimulationShipmentEntity> entities = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.ISO_8859_1))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty() || line.trim().startsWith("//")) continue;

                String[] parts = line.trim().split("-");
                if (parts.length == 7) {
                    String destIcao = parts[4];
                    if (destIcao.equals(icao)) {
                        errors.add("Origen igual a destino ignorado: " + line.trim());
                        continue;
                    }
                    if (!airports.containsKey(destIcao)) {
                        errors.add("Aeropuerto destino no registrado: " + destIcao + " (línea: " + line.trim() + ")");
                        continue;
                    }
                }

                ShipmentDataDTO dto = ShipmentParser.parseLine(line, origin, airports, deliveryTypes);
                if (dto != null) {
                    entities.add(new SimulationShipmentEntity(
                            dto.getId(),
                            dto.getOriginAirport().getIcao(),
                            dto.getDestAirport().getIcao(),
                            dto.getEntryDateTimeUtc(),
                            (short) dto.getQuantity(),
                            dto.getClientId()));
                }
            }
        }

        if (replace) shipmentRepo.deleteByOriginIcao(icao);

        for (int i = 0; i < entities.size(); i += BATCH_SIZE) {
            shipmentRepo.saveAll(entities.subList(i, Math.min(i + BATCH_SIZE, entities.size())));
            em.flush();
            em.clear();
        }

        return new LoadResult(entities.size(), errors, List.of());
    }

    @Transactional
    public int deleteHistoricalByAirport(String icao) {
        if (!airportRepo.existsById(icao))
            throw new IllegalArgumentException("Aeropuerto no registrado en BD: " + icao);
        AirportShipmentDetail detail = getHistoricalDetail(icao);
        shipmentRepo.deleteByOriginIcao(icao);
        return detail.total();
    }

    // ── Records de respuesta ──────────────────────────────────────────────────

    public record AirportShipmentStatus(String icao, int count) {}
    public record DailyCount(String date, int count) {}
    public record AirportShipmentDetail(String icao, int total, List<DailyCount> byDay) {}

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, AirportDataDTO> loadAirportsFromDb() {
        return airportRepo.findAll().stream()
                .collect(Collectors.toMap(AirportEntity::getIcao, e -> new AirportDataDTO(
                        e.getIcao(), e.getCity(), e.getCountry(), e.getContinent(),
                        e.getShortName(), e.getGmtOffset(), e.getCapacity(),
                        e.getLatitude(), e.getLongitude())));
    }

    private AirportEntity toAirportEntity(AirportDataDTO dto) {
        return new AirportEntity(
                dto.getIcao(), dto.getCity(), dto.getCountry(), dto.getContinent(),
                dto.getShort_name(), (short) dto.getGmtOffset(), dto.getCapacity(),
                dto.getLatitude(), dto.getLongitude());
    }

    private FlightScheduleEntity toFlightEntity(FlightScheduleDataDTO dto) {
        return new FlightScheduleEntity(
                dto.getId(),
                dto.getOriginAirport().getIcao(),
                dto.getDestAirport().getIcao(),
                dto.getDepartureTimeLocal(),
                dto.getArrivalTimeLocal(),
                dto.getCapacity());
    }
}
