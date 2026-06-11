// FlightParser.java
package com.tasf.b2b.infrastructure.files;

import com.tasf.b2b.domain.model.graph.immovable.AirportDataDTO;
import com.tasf.b2b.domain.model.graph.immovable.FlightScheduleDataDTO;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FlightParser {

    // Formato por línea: ORIG-DEST-HH:MM-HH:MM-capacidad
    // Líneas que empiezan con // son comentarios
    public static List<FlightScheduleDataDTO> parse(Path file, Map<String, AirportDataDTO> airports)
            throws IOException {
        try (InputStream is = Files.newInputStream(file)) {
            return parse(is, airports);
        }
    }

    /** Parsea vuelos desde un InputStream (ISO-8859-1). Usado para uploads multipart. */
    public static List<FlightScheduleDataDTO> parse(InputStream is, Map<String, AirportDataDTO> airports)
            throws IOException {
        List<FlightScheduleDataDTO> flights = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.ISO_8859_1))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("//")) continue;

                String[] parts = line.split("-");
                if (parts.length != 5)
                    throw new IllegalArgumentException("Línea de vuelo inválida: " + line);

                String    origIcao = parts[0];
                String    destIcao = parts[1];
                LocalTime depTime  = LocalTime.parse(parts[2]);
                LocalTime arrTime  = LocalTime.parse(parts[3]);
                int       capacity = Integer.parseInt(parts[4]);

                AirportDataDTO orig = airports.get(origIcao);
                AirportDataDTO dest = airports.get(destIcao);

                if (orig == null || dest == null)
                    throw new IllegalArgumentException(
                            "Aeropuerto no registrado: " + origIcao + " o " + destIcao);

                flights.add(new FlightScheduleDataDTO(orig, dest, depTime, arrTime, capacity));
            }
        }
        return flights;
    }
}
