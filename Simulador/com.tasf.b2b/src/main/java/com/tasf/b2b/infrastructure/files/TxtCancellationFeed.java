package com.tasf.b2b.infrastructure.files;

import com.tasf.b2b.domain.model.graph.immovable.AirportDataDTO;
import com.tasf.b2b.domain.simulator.feed.CancellationEntry;
import com.tasf.b2b.domain.simulator.feed.CancellationFeed;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Streaming sobre cancelaciones.txt, devuelve CancellationEntry en orden cronológico.
 * Cierra el reader internamente cuando se agota.
 */
public class TxtCancellationFeed implements CancellationFeed {

    private final Map<String, AirportDataDTO> airports;
    private       BufferedReader              reader;
    private       boolean                     exhausted;

    public TxtCancellationFeed(Path cancellationFile, Map<String, AirportDataDTO> airports) {
        this.airports = airports;

        if (!Files.exists(cancellationFile)) {
            System.out.println("[FEED] Archivo de cancelaciones no encontrado — sin cancelaciones.");
            this.reader   = null;
            this.exhausted = true;
            return;
        }

        try {
            this.reader   = Files.newBufferedReader(cancellationFile, StandardCharsets.ISO_8859_1);
            this.exhausted = false;
        } catch (IOException e) {
            System.err.println("[FEED] Error abriendo cancelaciones: " + e.getMessage());
            this.reader   = null;
            this.exhausted = true;
        }
    }

    @Override
    public CancellationEntry next() {
        if (exhausted) return null;

        try {
            String line;
            while ((line = reader.readLine()) != null) {
                CancellationEntry entry = CancellationParser.parseLine(line, airports);
                if (entry != null) return entry;
            }
        } catch (IOException e) {
            System.err.println("[FEED] Error leyendo cancelación: " + e.getMessage());
        }

        close();
        return null;
    }

    private void close() {
        if (exhausted) return;
        exhausted = true;
        try { if (reader != null) reader.close(); } catch (IOException ignored) {}
        reader = null;
    }
}
