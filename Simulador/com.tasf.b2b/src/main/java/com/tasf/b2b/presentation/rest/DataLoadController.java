package com.tasf.b2b.presentation.rest;

import com.tasf.b2b.infrastructure.persistence.adapter.DataLoadService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Carga de datos de referencia e históricos desde archivos TXT.
 *
 * POST /api/v1/admin/airports?mode=replace|merge   → sube aeropuertos
 * POST /api/v1/admin/flights?mode=replace|merge    → sube vuelos programados
 * POST /api/v1/admin/shipments                     → carga histórica (solo si tabla vacía)
 * DELETE /api/v1/admin/shipments                   → borra todos los shipments
 */
@RestController
@RequestMapping("/api/v1/admin")
public class DataLoadController {

    private final DataLoadService service;

    public DataLoadController(DataLoadService service) {
        this.service = service;
    }

    record LoadResponse(String message, int count, List<String> errors, List<String> warnings) {}

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
