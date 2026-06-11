package com.tasf.b2b.infrastructure.files;

import com.tasf.b2b.application.port.in.AvailableDaysPort;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Implementación de AvailableDaysPort que escanea los archivos _envios_ICAO_.txt
 * para determinar qué fechas tienen datos de envíos.
 *
 * Formato de línea: 000000001-20260102-00-55-SPIM-002-0019169
 *                                   ↑ parts[1] = fecha yyyyMMdd
 *
 * Escanea los archivos una sola vez al llamar getAvailableDates() y no cachea
 * el resultado (los archivos podrían cambiar entre llamadas).
 * Si se necesita caché, el servicio que llama puede guardar la lista.
 *
 * Vive en infrastructure porque usa directamente los archivos .txt — viola la
 * regla domain→nada si estuviera en domain o application.
 */
public class TxtAvailableDaysService implements AvailableDaysPort {

    private static final Pattern SHIPMENT_FILE = Pattern.compile("_envios_[A-Z]{4}_\\.txt");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    // TODO: reemplazar por query a BD cuando esté disponible
    private final List<LocalDate> availableDates;

    public TxtAvailableDaysService(Path shipmentDir) {
        this.availableDates = scanFiles(shipmentDir);
        System.out.printf("[DAYS] %d fechas disponibles%n", availableDates.size());
    }

    @Override
    public List<LocalDate> getAvailableDates() {
        return availableDates;
    }

    private List<LocalDate> scanFiles(Path shipmentDir) {
        TreeSet<LocalDate> dates = new TreeSet<>();
        try (Stream<Path> files = Files.list(shipmentDir)) {
            files.filter(p -> SHIPMENT_FILE.matcher(p.getFileName().toString()).matches())
                 .forEach(file -> scanFile(file, dates));
        } catch (IOException e) {
            System.err.println("[DAYS] Error listando directorio: " + e.getMessage());
        }
        return new ArrayList<>(dates);
    }

    private void scanFile(Path file, TreeSet<LocalDate> dates) {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.ISO_8859_1)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                // Saltar cabeceras y vacíos (mismo criterio que ShipmentParser.skipHeader)
                if (line.isEmpty() || line.startsWith("//")) continue;

                String[] parts = line.split("-");
                if (parts.length != 7) continue;

                try {
                    LocalDate date = LocalDate.parse(parts[1], DATE_FMT);
                    dates.add(date);
                } catch (Exception ignored) {
                    // Línea con fecha malformada — ignorar silenciosamente
                }
            }
        } catch (IOException e) {
            System.err.println("[DAYS] Error leyendo " + file.getFileName() + ": " + e.getMessage());
        }
    }
}
