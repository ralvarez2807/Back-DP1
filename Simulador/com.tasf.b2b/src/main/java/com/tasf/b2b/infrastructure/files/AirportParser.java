// AirportParser.java
package com.tasf.b2b.infrastructure.files;

import com.tasf.b2b.domain.model.graph.immovable.AirportDataDTO;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AirportParser {

    // Formato por línea (UTF-16):
    // 01   SKBO   Bogota   Colombia   bogo   -5   430   Latitude: 04° 42' 05" N   Longitude: 74° 08' 49" W
    private static final Pattern LINE_PATTERN = Pattern.compile(
            "\\d+\\s+" +
            "([A-Z]{4})\\s+" +
            "(.+?)\\s{2,}" +
            "(.+?)\\s{2,}" +
            "(\\S+)\\s+" +
            "([+-]?\\d+)\\s+" +
            "(\\d+)\\s+" +
            "Latitude:\\s*(\\d+)[°º]\\s*(\\d+)'\\s*([\\d.]+)[\"']\\s*([NS])\\s+" +
            "Longitude:\\s*(\\d+)[°º]\\s*(\\d+)'\\s*([\\d.]+)[\"']\\s*([EW])"
    );

    /** Parsea el archivo de aeropuertos (UTF-16). Lee el encabezado de sección para determinar el continente. */
    public static Map<String, AirportDataDTO> parse(Path file) throws IOException {
        Map<String, AirportDataDTO> airports = new LinkedHashMap<>();
        String currentContinent = "Desconocido";

        for (String line : Files.readAllLines(file, StandardCharsets.UTF_16)) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("*") || line.startsWith("P")) continue;

            // Detectar encabezado de sección de continente antes de intentar parsear aeropuerto
            String detected = detectContinent(line);
            if (detected != null) {
                currentContinent = detected;
                continue;
            }

            Matcher m = LINE_PATTERN.matcher(line);
            if (!m.find()) continue;

            String icao      = m.group(1);
            String city      = m.group(2).trim();
            String country   = m.group(3).trim();
            String shortName = m.group(4);
            int    gmtOffset = Integer.parseInt(m.group(5));
            int    capacity  = Integer.parseInt(m.group(6));

            double latitude = AirportDataDTO.fromGMSToDecimal(
                    Integer.parseInt(m.group(7)),
                    Integer.parseInt(m.group(8)),
                    Double.parseDouble(m.group(9)),
                    m.group(10).charAt(0));

            double longitude = AirportDataDTO.fromGMSToDecimal(
                    Integer.parseInt(m.group(11)),
                    Integer.parseInt(m.group(12)),
                    Double.parseDouble(m.group(13)),
                    m.group(14).charAt(0));

            airports.put(icao, new AirportDataDTO(
                    icao, city, country, currentContinent,
                    shortName, gmtOffset, capacity, latitude, longitude));
        }

        return airports;
    }

    /** Parsea aeropuertos desde un InputStream (UTF-16). Usado para uploads multipart. */
    public static Map<String, AirportDataDTO> parse(InputStream is) throws IOException {
        Map<String, AirportDataDTO> airports = new LinkedHashMap<>();
        String currentContinent = "Desconocido";

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_16))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("*") || line.startsWith("P")) continue;

                String detected = detectContinent(line);
                if (detected != null) { currentContinent = detected; continue; }

                Matcher m = LINE_PATTERN.matcher(line);
                if (!m.find()) continue;

                String icao      = m.group(1);
                String city      = m.group(2).trim();
                String country   = m.group(3).trim();
                String shortName = m.group(4);
                int    gmtOffset = Integer.parseInt(m.group(5));
                int    capacity  = Integer.parseInt(m.group(6));

                double latitude = AirportDataDTO.fromGMSToDecimal(
                        Integer.parseInt(m.group(7)), Integer.parseInt(m.group(8)),
                        Double.parseDouble(m.group(9)), m.group(10).charAt(0));
                double longitude = AirportDataDTO.fromGMSToDecimal(
                        Integer.parseInt(m.group(11)), Integer.parseInt(m.group(12)),
                        Double.parseDouble(m.group(13)), m.group(14).charAt(0));

                airports.put(icao, new AirportDataDTO(
                        icao, city, country, currentContinent,
                        shortName, gmtOffset, capacity, latitude, longitude));
            }
        }
        return airports;
    }

    // Detecta si la línea es un encabezado de sección de continente.
    // Los encabezados empiezan con el nombre del continente (sin número de secuencia).
    private static String detectContinent(String line) {
        if (line.startsWith("America del Sur")) return "America del Sur";
        if (line.startsWith("Europa"))          return "Europa";
        if (line.startsWith("Asia"))            return "Asia";
        return null;
    }
}
