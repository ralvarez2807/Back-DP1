// CancellationParser.java
package com.tasf.b2b.infrastructure.files;

import com.tasf.b2b.domain.model.graph.immovable.AirportDataDTO;
import com.tasf.b2b.domain.simulator.feed.CancellationEntry;
import com.tasf.b2b.domain.util.TimeUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CancellationParser {

    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** Carga completa de un archivo de cancelaciones (uso al inicio si se requiere). */
    public static List<CancellationEntry> parse(Path file, Map<String, AirportDataDTO> airports)
            throws IOException {
        List<CancellationEntry> cancellations = new ArrayList<>();

        for (String line : Files.readAllLines(file, StandardCharsets.ISO_8859_1)) {
            CancellationEntry c = parseLine(line, airports);
            if (c != null) cancellations.add(c);
        }

        return cancellations;
    }

    /**
     * Parsea una línea individual (para lectura en streaming).
     * Formato: SKBO-SEQM-19:01  20260102
     *   campo 1: flightKey  (ORIG-DEST-HH:mm, separado con espacio del campo 2)
     *   campo 2: día local  (yyyyMMdd) en la zona horaria del aeropuerto origen
     *
     * Devuelve null si la línea es vacía, comentario o inválida.
     */
    public static CancellationEntry parseLine(String line, Map<String, AirportDataDTO> airports) {
        if (line == null) return null;
        line = line.trim();
        if (line.isEmpty() || line.startsWith("//")) return null;

        String[] parts = line.split("\\s+");
        if (parts.length != 2) {
            System.err.println("  ⚠ Línea de cancelación inválida (ignorada): " + line);
            return null;
        }

        try {
            String flightKey = parts[0];  // SKBO-SEQM-19:01
            String dayStr    = parts[1];  // 20260102

            // Extrae ICAO origen y hora de salida local del flightKey
            String[] keyParts = flightKey.split("-");
            if (keyParts.length != 3) {
                System.err.println("  ⚠ FlightKey inválido (ignorado): " + flightKey);
                return null;
            }
            String    origIcao = keyParts[0];
            LocalTime depLocal = LocalTime.parse(keyParts[2]);

            AirportDataDTO orig = airports.get(origIcao);
            if (orig == null) {
                System.err.println("  ⚠ Aeropuerto origen no registrado (ignorado): " + origIcao);
                return null;
            }

            // Combina día local + hora local → UTC exacto de salida
            LocalDate     date     = LocalDate.parse(dayStr, DAY_FORMATTER);
            LocalDateTime localDep = LocalDateTime.of(date, depLocal);
            var           depUtc   = TimeUtils.localToUtc(localDep, orig.getGmtOffset());

            return new CancellationEntry(flightKey, depUtc);

        } catch (Exception e) {
            System.err.println("  ⚠ Error parseando cancelación (ignorada): " + line + " → " + e.getMessage());
            return null;
        }
    }
}
