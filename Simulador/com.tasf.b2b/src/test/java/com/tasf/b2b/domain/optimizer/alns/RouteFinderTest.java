package com.tasf.b2b.domain.optimizer.alns;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RouteFinderTest {

    private static final int PICKUP_MIN  = 0; // sin margen para simplificar asserts
    private static final int CONNECT_MIN = 0;

    // ── helpers ──────────────────────────────────────────────────────────────

    private static AlnsProjection projection(List<FlightSnapshot> flights) {
        Map<String, NavigableMap<Instant, List<FlightSnapshot>>> byOrigin = new HashMap<>();
        for (FlightSnapshot f : flights) {
            byOrigin.computeIfAbsent(f.fromIcao(), k -> new TreeMap<>())
                    .computeIfAbsent(f.depTime(), k -> new ArrayList<>())
                    .add(f);
        }
        return new AlnsProjection(List.of(), byOrigin, CONNECT_MIN, PICKUP_MIN,
                Instant.EPOCH, Map.of(), Map.of());
    }

    private static FlightSnapshot flight(String id, String from, String to,
                                         Instant dep, Instant arr, int cap) {
        return new FlightSnapshot(id, from, to, dep, arr, cap);
    }

    private static BaggageState baggage(String origin, String dest,
                                        Instant availableFrom, Instant deadline) {
        return new BaggageState("B1", origin, availableFrom, dest, deadline);
    }

    private static BaggageSolution emptySolution(BaggageState b) {
        return BaggageSolution.empty(List.of(b));
    }

    // Proyección con márgenes reales (los de producción: 10 min escala, 15 min recojo)
    private static AlnsProjection projectionConMargenes(List<FlightSnapshot> flights) {
        Map<String, NavigableMap<Instant, List<FlightSnapshot>>> byOrigin = new HashMap<>();
        for (FlightSnapshot f : flights) {
            byOrigin.computeIfAbsent(f.fromIcao(), k -> new TreeMap<>())
                    .computeIfAbsent(f.depTime(), k -> new ArrayList<>())
                    .add(f);
        }
        return new AlnsProjection(List.of(), byOrigin, 10, 15,
                Instant.EPOCH, Map.of(), Map.of());
    }

    // ── margen en origen ─────────────────────────────────────────────────────

    @Test
    void toma_un_vuelo_que_sale_en_el_mismo_instante_en_que_esta_lista() {
        // availableFrom ya viene calculado por AlnsProjectionBuilder: findRoute no debe
        // sumarle ningún margen extra al primer tramo.
        Instant dep = Instant.parse("2026-01-02T10:00:00Z");
        Instant arr = Instant.parse("2026-01-02T12:00:00Z");
        FlightSnapshot f = flight("F1", "SKBO", "SEQM", dep, arr, 50);

        BaggageState b      = baggage("SKBO", "SEQM", dep, arr.plusSeconds(7200));
        BaggageSolution sol = emptySolution(b);

        List<FlightSnapshot> ruta = RouteFinder.findRoute(b, sol, projectionConMargenes(List.of(f)));

        assertEquals(1, ruta.size(), "el vuelo sale justo cuando la maleta está lista: es tomable");
        assertEquals("F1", ruta.get(0).flightId());
    }

    @Test
    void descarta_un_vuelo_que_sale_antes_de_que_la_maleta_este_lista() {
        Instant listo = Instant.parse("2026-01-02T10:00:00Z");
        FlightSnapshot f = flight("F1", "SKBO", "SEQM",
                listo.minusSeconds(60),               // salió un minuto antes
                Instant.parse("2026-01-02T12:00:00Z"), 50);

        BaggageState b      = baggage("SKBO", "SEQM", listo, Instant.parse("2026-01-02T20:00:00Z"));
        BaggageSolution sol = emptySolution(b);

        List<FlightSnapshot> ruta = RouteFinder.findRoute(b, sol, projectionConMargenes(List.of(f)));

        assertTrue(ruta.isEmpty());
    }

    @Test
    void respeta_el_margen_de_escala_entre_tramos() {
        Instant dep1 = Instant.parse("2026-01-02T10:00:00Z");
        Instant arr1 = Instant.parse("2026-01-02T12:00:00Z");
        // Conexión a 5 min del aterrizaje: por debajo de los 10 min de escala
        FlightSnapshot tramo1  = flight("F1", "SKBO", "SEQM", dep1, arr1, 50);
        FlightSnapshot muyJusto = flight("F2", "SEQM", "EHAM",
                arr1.plusSeconds(300), arr1.plusSeconds(3600), 50);

        BaggageState b      = baggage("SKBO", "EHAM", dep1, Instant.parse("2026-01-03T00:00:00Z"));
        BaggageSolution sol = emptySolution(b);

        List<FlightSnapshot> ruta = RouteFinder.findRoute(b, sol,
                projectionConMargenes(List.of(tramo1, muyJusto)));

        assertTrue(ruta.isEmpty(), "la conexión de 5 min viola el margen de escala de 10 min");
    }

    @Test
    void acepta_la_escala_cuando_supera_el_margen() {
        Instant dep1 = Instant.parse("2026-01-02T10:00:00Z");
        Instant arr1 = Instant.parse("2026-01-02T12:00:00Z");
        FlightSnapshot tramo1 = flight("F1", "SKBO", "SEQM", dep1, arr1, 50);
        FlightSnapshot tramo2 = flight("F2", "SEQM", "EHAM",
                arr1.plusSeconds(600), arr1.plusSeconds(3600), 50);  // exactamente 10 min

        BaggageState b      = baggage("SKBO", "EHAM", dep1, Instant.parse("2026-01-03T00:00:00Z"));
        BaggageSolution sol = emptySolution(b);

        List<FlightSnapshot> ruta = RouteFinder.findRoute(b, sol,
                projectionConMargenes(List.of(tramo1, tramo2)));

        assertEquals(2, ruta.size());
    }

    @Test
    void el_ultimo_tramo_debe_aterrizar_con_el_margen_de_recojo_antes_del_deadline() {
        Instant dep = Instant.parse("2026-01-02T10:00:00Z");
        Instant arr = Instant.parse("2026-01-02T12:00:00Z");
        FlightSnapshot f = flight("F1", "SKBO", "SEQM", dep, arr, 50);

        // Deadline 10 min después del aterrizaje: no alcanza para los 15 min de recojo
        BaggageState b      = baggage("SKBO", "SEQM", dep, arr.plusSeconds(600));
        BaggageSolution sol = emptySolution(b);

        List<FlightSnapshot> ruta = RouteFinder.findRoute(b, sol, projectionConMargenes(List.of(f)));

        assertTrue(ruta.isEmpty(), "la entrega real es aterrizaje + 15 min y excede el deadline");
    }

    // ── findRoute ────────────────────────────────────────────────────────────

    @Test
    void ruta_directa_cuando_hay_vuelo_origen_destino() {
        Instant dep = Instant.parse("2026-01-02T10:00:00Z");
        Instant arr = Instant.parse("2026-01-02T12:00:00Z");
        FlightSnapshot f = flight("F1", "SKBO", "SEQM", dep, arr, 50);

        BaggageState b       = baggage("SKBO", "SEQM", dep, arr.plusSeconds(3600));
        BaggageSolution sol  = emptySolution(b);
        AlnsProjection proj  = projection(List.of(f));

        List<FlightSnapshot> ruta = RouteFinder.findRoute(b, sol, proj);

        assertEquals(1, ruta.size());
        assertEquals("F1", ruta.get(0).flightId());
    }

    @Test
    void sin_ruta_cuando_no_hay_vuelos_desde_origen() {
        BaggageState b      = baggage("SKBO", "SEQM",
                Instant.parse("2026-01-02T10:00:00Z"),
                Instant.parse("2026-01-02T20:00:00Z"));
        BaggageSolution sol = emptySolution(b);
        AlnsProjection proj = projection(List.of()); // sin vuelos

        List<FlightSnapshot> ruta = RouteFinder.findRoute(b, sol, proj);

        assertTrue(ruta.isEmpty());
    }

    @Test
    void sin_ruta_cuando_todos_los_vuelos_superan_el_deadline() {
        Instant dep = Instant.parse("2026-01-02T10:00:00Z");
        Instant arr = Instant.parse("2026-01-02T15:00:00Z");
        Instant deadline = Instant.parse("2026-01-02T14:00:00Z"); // antes de la llegada

        FlightSnapshot f = flight("F1", "SKBO", "SEQM", dep, arr, 50);
        BaggageState b   = baggage("SKBO", "SEQM", dep, deadline);
        BaggageSolution sol = emptySolution(b);
        AlnsProjection proj = projection(List.of(f));

        List<FlightSnapshot> ruta = RouteFinder.findRoute(b, sol, proj);

        assertTrue(ruta.isEmpty());
    }

    @Test
    void sin_ruta_cuando_vuelo_sin_capacidad() {
        Instant dep = Instant.parse("2026-01-02T10:00:00Z");
        Instant arr = Instant.parse("2026-01-02T12:00:00Z");
        FlightSnapshot f = flight("F1", "SKBO", "SEQM", dep, arr, 0); // capacidad 0

        BaggageState b      = baggage("SKBO", "SEQM", dep, arr.plusSeconds(3600));
        BaggageSolution sol = emptySolution(b);
        AlnsProjection proj = projection(List.of(f));

        List<FlightSnapshot> ruta = RouteFinder.findRoute(b, sol, proj);

        assertTrue(ruta.isEmpty());
    }

    @Test
    void ruta_con_escala_cuando_no_hay_vuelo_directo() {
        Instant dep1 = Instant.parse("2026-01-02T08:00:00Z");
        Instant arr1 = Instant.parse("2026-01-02T10:00:00Z");
        Instant dep2 = Instant.parse("2026-01-02T11:00:00Z");
        Instant arr2 = Instant.parse("2026-01-02T20:00:00Z");
        Instant deadline = Instant.parse("2026-01-02T22:00:00Z");

        FlightSnapshot f1 = flight("F1", "SKBO", "BOGM", dep1, arr1, 50);
        FlightSnapshot f2 = flight("F2", "BOGM", "SEQM", dep2, arr2, 50);

        BaggageState b      = baggage("SKBO", "SEQM", dep1, deadline);
        BaggageSolution sol = emptySolution(b);
        AlnsProjection proj = projection(List.of(f1, f2));

        List<FlightSnapshot> ruta = RouteFinder.findRoute(b, sol, proj);

        assertEquals(2, ruta.size());
        assertEquals("F1", ruta.get(0).flightId());
        assertEquals("F2", ruta.get(1).flightId());
    }

    @Test
    void no_usa_vuelos_en_la_lista_negra() {
        Instant dep = Instant.parse("2026-01-02T10:00:00Z");
        Instant arr = Instant.parse("2026-01-02T12:00:00Z");
        FlightSnapshot f = flight("F1", "SKBO", "SEQM", dep, arr, 50);

        BaggageState b      = baggage("SKBO", "SEQM", dep, arr.plusSeconds(3600));
        BaggageSolution sol = emptySolution(b);
        AlnsProjection proj = projection(List.of(f));

        List<FlightSnapshot> ruta = RouteFinder.findRoute(b, sol, proj, Set.of("F1"));

        assertTrue(ruta.isEmpty());
    }

    @Test
    void no_usa_vuelo_cuya_capacidad_esta_ocupada_por_solucion_actual() {
        Instant dep = Instant.parse("2026-01-02T10:00:00Z");
        Instant arr = Instant.parse("2026-01-02T12:00:00Z");
        FlightSnapshot f = flight("F1", "SKBO", "SEQM", dep, arr, 1); // cap=1

        BaggageState b1 = new BaggageState("B1", "SKBO", dep, "SEQM", arr.plusSeconds(3600));
        BaggageState b2 = new BaggageState("B2", "SKBO", dep, "SEQM", arr.plusSeconds(3600));
        BaggageSolution sol = BaggageSolution.empty(List.of(b1, b2));
        sol.addRoute(b1, List.of(f)); // b1 ocupa la única plaza
        AlnsProjection proj = projection(List.of(f));

        List<FlightSnapshot> ruta = RouteFinder.findRoute(b2, sol, proj);

        assertTrue(ruta.isEmpty());
    }

    // ── findRouteMinHops ─────────────────────────────────────────────────────

    @Test
    void findRouteMinHops_prefiere_ruta_directa_sobre_ruta_rapida_con_escala() {
        // Vuelo directo lento pero en un hop
        Instant dep  = Instant.parse("2026-01-02T10:00:00Z");
        Instant arrD = Instant.parse("2026-01-02T20:00:00Z"); // llega tarde
        FlightSnapshot directo = flight("D", "SKBO", "SEQM", dep, arrD, 50);

        // Ruta de 2 hops que llegaría antes
        Instant arr1 = Instant.parse("2026-01-02T12:00:00Z");
        Instant dep2 = Instant.parse("2026-01-02T13:00:00Z");
        Instant arr2 = Instant.parse("2026-01-02T15:00:00Z");
        FlightSnapshot h1 = flight("H1", "SKBO", "MID",  dep,  arr1, 50);
        FlightSnapshot h2 = flight("H2", "MID",  "SEQM", dep2, arr2, 50);

        Instant deadline = Instant.parse("2026-01-02T22:00:00Z");
        BaggageState b      = baggage("SKBO", "SEQM", dep, deadline);
        BaggageSolution sol = emptySolution(b);
        AlnsProjection proj = projection(List.of(directo, h1, h2));

        List<FlightSnapshot> ruta = RouteFinder.findRouteMinHops(b, sol, proj);

        assertEquals(1, ruta.size());
        assertEquals("D", ruta.get(0).flightId());
    }
}
