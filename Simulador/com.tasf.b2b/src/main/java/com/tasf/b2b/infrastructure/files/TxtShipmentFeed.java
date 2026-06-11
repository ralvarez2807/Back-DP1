package com.tasf.b2b.infrastructure.files;

import com.tasf.b2b.domain.model.graph.immovable.AirportDataDTO;
import com.tasf.b2b.domain.model.graph.immovable.DeliveryTypeValues;
import com.tasf.b2b.domain.model.graph.immovable.ShipmentDataDTO;
import com.tasf.b2b.domain.simulator.feed.ShipmentFeed;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * K-way merge sobre los archivos _envios_XXXX_.txt de un directorio.
 * Devuelve ShipmentDataDTO en orden cronológico estricto.
 * Cierra los readers internamente cuando se agota.
 */
public class TxtShipmentFeed implements ShipmentFeed {

    private final Map<String, AirportDataDTO> airports;
    private final DeliveryTypeValues          deliveryTypes;
    private final PriorityQueue<FileEntry>    heap;
    private final List<BufferedReader>        readers;
    private       boolean                     exhausted;

    private record FileEntry(ShipmentDataDTO dto, BufferedReader reader, AirportDataDTO origin)
            implements Comparable<FileEntry> {
        @Override
        public int compareTo(FileEntry o) {
            return dto.getEntryDateTimeUtc().compareTo(o.dto.getEntryDateTimeUtc());
        }
    }

    public TxtShipmentFeed(Path shipmentDir,
                           Map<String, AirportDataDTO> airports,
                           DeliveryTypeValues deliveryTypes) {
        this.airports      = airports;
        this.deliveryTypes = deliveryTypes;
        this.heap          = new PriorityQueue<>();
        this.readers       = new ArrayList<>();
        this.exhausted     = false;

        for (AirportDataDTO airport : airports.values()) {
            Path file = shipmentDir.resolve("_envios_" + airport.getIcao() + "_.txt");
            if (!Files.exists(file)) continue;

            try {
                BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.ISO_8859_1);
                readers.add(reader);
                ShipmentParser.skipHeader(reader);
                FileEntry first = readNext(reader, airport);
                if (first != null) heap.offer(first);
            } catch (IOException e) {
                System.err.println("[FEED] Error abriendo " + file + ": " + e.getMessage());
            }
        }

        System.out.println("[FEED] Archivos de envíos abiertos: " + readers.size());
    }

    @Override
    public ShipmentDataDTO next() {
        if (exhausted) return null;

        if (heap.isEmpty()) {
            close();
            return null;
        }

        FileEntry entry = heap.poll();
        FileEntry next  = readNext(entry.reader(), entry.origin());
        if (next != null) heap.offer(next);

        if (heap.isEmpty()) close();

        return entry.dto();
    }

    private FileEntry readNext(BufferedReader reader, AirportDataDTO origin) {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                ShipmentDataDTO dto = ShipmentParser.parseLine(line, origin, airports, deliveryTypes);
                if (dto != null) return new FileEntry(dto, reader, origin);
            }
        } catch (IOException e) {
            System.err.println("[FEED] Error leyendo línea: " + e.getMessage());
        }
        return null;
    }

    private void close() {
        if (exhausted) return;
        exhausted = true;
        for (BufferedReader r : readers) {
            try { r.close(); } catch (IOException ignored) {}
        }
        readers.clear();
    }
}
