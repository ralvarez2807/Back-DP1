package com.tasf.b2b.domain.optimizer.alns;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BaggageSolutionTest {

    private static final Instant T0       = Instant.parse("2026-01-02T00:00:00Z");
    private static final Instant DEADLINE = Instant.parse("2026-01-02T12:00:00Z");

    private static BaggageState baggage(String id) {
        return new BaggageState(id, "SKBO", T0, "SEQM", DEADLINE);
    }

    private static FlightSnapshot flight(String id, Instant dep, Instant arr, int cap) {
        return new FlightSnapshot(id, "SKBO", "SEQM", dep, arr, cap);
    }

    // ── score ────────────────────────────────────────────────────────────────

    @Test
    void score_1000_por_baggage_sin_ruta() {
        BaggageState b1 = baggage("B1");
        BaggageState b2 = baggage("B2");
        BaggageSolution sol = BaggageSolution.empty(List.of(b1, b2));
        // No se agregan rutas → ambos en unrouted

        assertEquals(2000.0, sol.score(), 0.001);
    }

    @Test
    void score_0_para_baggage_ruteado_a_tiempo() {
        BaggageState b = baggage("B1");
        BaggageSolution sol = BaggageSolution.empty(List.of(b));

        Instant arr = DEADLINE.minusSeconds(3600); // llega 1h antes del deadline
        FlightSnapshot f = flight("F1", T0, arr, 10);
        sol.addRoute(b, List.of(f));

        assertEquals(0.0, sol.score(), 0.001);
    }

    @Test
    void score_penaliza_horas_de_retraso() {
        BaggageState b = baggage("B1");
        BaggageSolution sol = BaggageSolution.empty(List.of(b));

        Instant arr = DEADLINE.plusSeconds(7200); // llega 2h tarde
        FlightSnapshot f = flight("F1", T0, arr, 10);
        sol.addRoute(b, List.of(f));

        assertEquals(2.0, sol.score(), 0.001); // 2 horas de penalización
    }

    @Test
    void score_combina_sin_ruta_y_retraso() {
        BaggageState b1 = baggage("B1");
        BaggageState b2 = baggage("B2");
        BaggageSolution sol = BaggageSolution.empty(List.of(b1, b2));

        // b1 sin ruta → 1000
        // b2 llega 1h tarde → 1.0
        Instant arr = DEADLINE.plusSeconds(3600);
        sol.addRoute(b2, List.of(flight("F1", T0, arr, 10)));

        assertEquals(1001.0, sol.score(), 0.001);
    }

    // ── deepCopy ─────────────────────────────────────────────────────────────

    @Test
    void deepCopy_es_independiente_del_original() {
        BaggageState b = baggage("B1");
        BaggageSolution original = BaggageSolution.empty(List.of(b));
        FlightSnapshot f = flight("F1", T0, DEADLINE.minusSeconds(3600), 10);
        original.addRoute(b, List.of(f));

        BaggageSolution copia = original.deepCopy();
        copia.removeRoute(b);

        // El original no debe verse afectado
        assertTrue(original.hasRoute(b));
        assertFalse(copia.hasRoute(b));
    }

    @Test
    void deepCopy_score_igual_al_original() {
        BaggageState b = baggage("B1");
        BaggageSolution sol = BaggageSolution.empty(List.of(b));
        sol.addRoute(b, List.of(flight("F1", T0, DEADLINE.plusSeconds(3600), 10)));

        BaggageSolution copia = sol.deepCopy();

        assertEquals(sol.score(), copia.score(), 0.001);
    }

    // ── flightHasCapacity ────────────────────────────────────────────────────

    @Test
    void flightHasCapacity_true_cuando_hay_espacio() {
        FlightSnapshot f = flight("F1", T0, DEADLINE, 5); // cap=5, extra=0
        BaggageSolution sol = BaggageSolution.empty(List.of());

        assertTrue(sol.flightHasCapacity(f));
    }

    @Test
    void flightHasCapacity_false_cuando_esta_lleno_por_solucion_actual() {
        BaggageState b = baggage("B1");
        FlightSnapshot f = flight("F1", T0, DEADLINE, 1); // cap=1

        BaggageSolution sol = BaggageSolution.empty(List.of(b));
        sol.addRoute(b, List.of(f)); // ocupa la única plaza

        assertFalse(sol.flightHasCapacity(f));
    }

    // ── addRoute / removeRoute ────────────────────────────────────────────────

    @Test
    void removeRoute_devuelve_baggage_a_unrouted() {
        BaggageState b = baggage("B1");
        BaggageSolution sol = BaggageSolution.empty(List.of(b));
        sol.addRoute(b, List.of(flight("F1", T0, DEADLINE, 10)));

        sol.removeRoute(b);

        assertFalse(sol.hasRoute(b));
        assertTrue(sol.isUnrouted(b));
    }

    @Test
    void addRoute_saca_baggage_de_unrouted() {
        BaggageState b = baggage("B1");
        BaggageSolution sol = BaggageSolution.empty(List.of(b));

        assertTrue(sol.isUnrouted(b));
        sol.addRoute(b, List.of(flight("F1", T0, DEADLINE, 10)));

        assertFalse(sol.isUnrouted(b));
        assertTrue(sol.hasRoute(b));
    }
}
