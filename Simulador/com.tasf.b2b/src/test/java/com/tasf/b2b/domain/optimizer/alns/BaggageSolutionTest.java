package com.tasf.b2b.domain.optimizer.alns;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BaggageSolutionTest {

    // Window de 12 h → ratio = (deadline - arrTime) / 12h
    private static final Instant T0          = Instant.parse("2026-01-02T00:00:00Z");
    private static final Instant DEADLINE    = Instant.parse("2026-01-02T12:00:00Z");
    // horizonMax 12 h después del deadline → ratio_unrouted = -1.0 → score = 2.0
    private static final Instant HORIZON_MAX = Instant.parse("2026-01-03T00:00:00Z");

    private static BaggageState baggage(String id) {
        return new BaggageState(id, "SKBO", T0, "SEQM", DEADLINE);
    }

    private static FlightSnapshot flight(String id, Instant dep, Instant arr, int cap) {
        return new FlightSnapshot(id, "SKBO", "SEQM", dep, arr, cap);
    }

    // ── score ────────────────────────────────────────────────────────────────

    @Test
    void score_baggage_sin_ruta_usa_horizonMax_como_arrTime() {
        // ratio = (DEADLINE - HORIZON_MAX) / 12h = -12h/12h = -1.0 → score = 2.0 por baggage
        BaggageState b1 = baggage("B1");
        BaggageState b2 = baggage("B2");
        BaggageSolution sol = BaggageSolution.empty(List.of(b1, b2));

        assertEquals(2.0, sol.score(HORIZON_MAX), 0.001);
    }

    @Test
    void score_optimo_cuando_llega_en_availableFrom() {
        // arrTime = T0 = availableFrom → ratio = 12h/12h = 1.0 → score = 0.0
        BaggageState b = baggage("B1");
        BaggageSolution sol = BaggageSolution.empty(List.of(b));

        FlightSnapshot f = flight("F1", T0, T0, 10);
        sol.addRoute(b, List.of(f));

        assertEquals(0.0, sol.score(HORIZON_MAX), 0.001);
    }

    @Test
    void score_mayor_que_1_cuando_llega_despues_del_deadline() {
        // arrTime = DEADLINE + 2h → ratio = -2h/12h = -1/6 → score = 1 + 1/6 ≈ 1.1667
        BaggageState b = baggage("B1");
        BaggageSolution sol = BaggageSolution.empty(List.of(b));

        Instant arr = DEADLINE.plusSeconds(7200); // +2h
        sol.addRoute(b, List.of(flight("F1", T0, arr, 10)));

        assertEquals(1.0 + 1.0 / 6.0, sol.score(HORIZON_MAX), 0.001);
    }

    @Test
    void score_usa_el_peor_ratio_entre_todos_los_baggages() {
        // b1 sin ruta → ratio = -1.0
        // b2 llega 2h tarde → ratio = -1/6
        // minRatio = -1.0 → score = 2.0
        BaggageState b1 = baggage("B1");
        BaggageState b2 = baggage("B2");
        BaggageSolution sol = BaggageSolution.empty(List.of(b1, b2));

        Instant arr = DEADLINE.plusSeconds(7200);
        sol.addRoute(b2, List.of(flight("F1", T0, arr, 10)));

        assertEquals(2.0, sol.score(HORIZON_MAX), 0.001);
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

        assertTrue(original.hasRoute(b));
        assertFalse(copia.hasRoute(b));
    }

    @Test
    void deepCopy_score_igual_al_original() {
        BaggageState b = baggage("B1");
        BaggageSolution sol = BaggageSolution.empty(List.of(b));
        sol.addRoute(b, List.of(flight("F1", T0, DEADLINE.plusSeconds(3600), 10)));

        BaggageSolution copia = sol.deepCopy();

        assertEquals(sol.score(HORIZON_MAX), copia.score(HORIZON_MAX), 0.001);
    }

    // ── flightHasCapacity ────────────────────────────────────────────────────

    @Test
    void flightHasCapacity_true_cuando_hay_espacio() {
        FlightSnapshot f = flight("F1", T0, DEADLINE, 5);
        BaggageSolution sol = BaggageSolution.empty(List.of());

        assertTrue(sol.flightHasCapacity(f));
    }

    @Test
    void flightHasCapacity_false_cuando_esta_lleno_por_solucion_actual() {
        BaggageState b = baggage("B1");
        FlightSnapshot f = flight("F1", T0, DEADLINE, 1);

        BaggageSolution sol = BaggageSolution.empty(List.of(b));
        sol.addRoute(b, List.of(f));

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
