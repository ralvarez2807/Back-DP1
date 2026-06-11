package com.tasf.b2b.presentation.rest;

import com.tasf.b2b.infrastructure.persistence.adapter.DataLoadService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Gestión de shipments históricos por aeropuerto origen.
 *
 * GET    /api/v1/admin/historical                        → aeropuertos con histórico cargado
 * GET    /api/v1/admin/historical/{icao}                 → detalle por fechas de un aeropuerto
 * POST   /api/v1/admin/historical/{icao}?mode=merge|replace → sube _envios_ICAO_.txt
 * DELETE /api/v1/admin/historical/{icao}                 → borra histórico de ese aeropuerto
 * DELETE /api/v1/admin/historical                        → borra todo el histórico
 */
@RestController
@RequestMapping("/api/v1/admin/historical")
public class HistoricalDataController {

    private final DataLoadService service;

    public HistoricalDataController(DataLoadService service) {
        this.service = service;
    }

    record LoadResponse(String message, int count, List<String> errors, List<String> warnings) {}

    // ── Consultas ─────────────────────────────────────────────────────────────

    @GetMapping
    public List<DataLoadService.AirportShipmentStatus> getStatus() {
        return service.getHistoricalStatus();
    }

    @GetMapping("/{icao}")
    public DataLoadService.AirportShipmentDetail getDetail(@PathVariable String icao) {
        try {
            return service.getHistoricalDetail(icao.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    // ── Carga ─────────────────────────────────────────────────────────────────

    @PostMapping(value = "/{icao}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public LoadResponse upload(
            @PathVariable String icao,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "mode", defaultValue = "merge") String mode) {
        try {
            String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
            if (!filename.toUpperCase().contains(icao.toUpperCase()))
                throw new IllegalArgumentException(
                        "El archivo '" + filename + "' no corresponde al aeropuerto " + icao);

            boolean replace = "replace".equalsIgnoreCase(mode);
            DataLoadService.LoadResult result = service.loadHistoricalFile(file, replace);
            String msg = replace
                    ? "Histórico de " + icao.toUpperCase() + " reemplazado"
                    : "Histórico de " + icao.toUpperCase() + " actualizado (merge)";
            return new LoadResponse(msg, result.count(), result.errors(), result.warnings());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    // ── Borrado ───────────────────────────────────────────────────────────────

    @DeleteMapping("/{icao}")
    public LoadResponse deleteByAirport(@PathVariable String icao) {
        try {
            int deleted = service.deleteHistoricalByAirport(icao.toUpperCase());
            return new LoadResponse("Histórico de " + icao.toUpperCase() + " eliminado", deleted, List.of(), List.of());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @DeleteMapping
    public LoadResponse deleteAll() {
        int deleted = service.deleteShipments();
        return new LoadResponse("Todo el histórico eliminado", deleted, List.of(), List.of());
    }
}
