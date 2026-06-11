// ShipmentParser.java
package com.tasf.b2b.infrastructure.files;

import com.tasf.b2b.domain.model.graph.immovable.AirportDataDTO;
import com.tasf.b2b.domain.model.graph.immovable.DeliveryTypeValues;
import com.tasf.b2b.domain.model.graph.immovable.ShipmentDataDTO;
import com.tasf.b2b.domain.util.TimeUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShipmentParser {

    // Extrae el ICAO origen del nombre del archivo: _envios_OJAI_.txt → OJAI
    private static final Pattern ICAO_FROM_FILENAME = Pattern.compile("_envios_([A-Z]{4})_");

    // Carga completa de un archivo (uso al inicio si se requiere)
    public static List<ShipmentDataDTO> parse(Path file,
                                              Map<String, AirportDataDTO> airports,
                                              DeliveryTypeValues deliveryTypeValues)
            throws IOException {
        String filename = file.getFileName().toString();
        Matcher matcher = ICAO_FROM_FILENAME.matcher(filename);
        if (!matcher.find())
            throw new IllegalArgumentException("Nombre de archivo inválido: " + filename);

        AirportDataDTO orig = airports.get(matcher.group(1));
        if (orig == null)
            throw new IllegalArgumentException("Aeropuerto no registrado: " + matcher.group(1));

        List<ShipmentDataDTO> shipments = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.ISO_8859_1)) {
            ShipmentDataDTO dto = parseLine(line, orig, airports, deliveryTypeValues);
            if (dto != null) shipments.add(dto);
        }
        return shipments;
    }

    /**
     * Salta cabeceras del reader (líneas vacías o con //).
     * El reader queda posicionado en la primera línea de datos.
     */
    public static void skipHeader(BufferedReader reader) throws IOException {
        reader.mark(4096);
        String line = reader.readLine();
        while (line != null && (line.trim().isEmpty() || line.trim().startsWith("//"))) {
            reader.mark(4096);
            line = reader.readLine();
        }
        if (line != null) reader.reset();
    }

    /**
     * Parsea una línea individual (para lectura en streaming).
     * Formato: 000000001-20260102-00-55-SPIM-002-0019169
     * Devuelve null si la línea es vacía, comentario o tiene formato inválido.
     */
    public static ShipmentDataDTO parseLine(String line,
                                            AirportDataDTO orig,
                                            Map<String, AirportDataDTO> airports,
                                            DeliveryTypeValues deliveryTypeValues) {
        if (line == null) return null;
        line = line.trim();
        if (line.isEmpty() || line.startsWith("//")) return null;

        String[] parts = line.split("-");
        if (parts.length != 7) {
            System.err.println("  ⚠ Línea de shipment inválida (ignorada): " + line);
            return null;
        }

        try {
            String id       = parts[0];
            String dateStr  = parts[1];
            int    hh       = Integer.parseInt(parts[2]);
            int    mm       = Integer.parseInt(parts[3]);
            String destIcao = parts[4];
            int    quantity = Integer.parseInt(parts[5]);
            String clientId = parts[6];

            AirportDataDTO dest = airports.get(destIcao);
            if (dest == null) {
                System.err.println("  ⚠ Aeropuerto destino no registrado (ignorado): " + destIcao);
                return null;
            }

            LocalDateTime localEntry = LocalDateTime.parse(
                    dateStr + String.format("%02d%02d", hh, mm),
                    DateTimeFormatter.ofPattern("yyyyMMddHHmm"));

            Instant entryUtc = TimeUtils.localToUtc(localEntry, orig.getGmtOffset());

            return new ShipmentDataDTO(id, entryUtc, orig, dest, quantity, clientId, deliveryTypeValues);

        } catch (Exception e) {
            System.err.println("  ⚠ Error parseando línea (ignorada): " + line + " → " + e.getMessage());
            return null;
        }
    }
}
