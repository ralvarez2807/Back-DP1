package com.tasf.b2b.domain.model.graph.movable;

import com.tasf.b2b.TestFixtures;
import com.tasf.b2b.domain.model.graph.componentsgraph.STEdge;
import com.tasf.b2b.domain.model.graph.componentsgraph.WaitEdge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.tasf.b2b.TestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class BaggageTest {

    private Shipment shipment;
    private Baggage  baggage;

    private WaitEdge edgeA;
    private WaitEdge edgeB;
    private WaitEdge edgeC;

    @BeforeEach
    void setUp() {
        shipment = TestFixtures.shipment("S1", SKBO, SEQM,
                Instant.parse("2026-01-02T00:00:00Z"), 1);
        baggage = shipment.getBaggages().get(0);

        Instant t0 = Instant.parse("2026-01-02T08:00:00Z");
        Instant t1 = Instant.parse("2026-01-02T10:00:00Z");
        Instant t2 = Instant.parse("2026-01-02T14:00:00Z");
        Instant t3 = Instant.parse("2026-01-02T18:00:00Z");

        edgeA = waitEdge(SKBO, t0, t1);
        edgeB = waitEdge(SKBO, t1, t2);
        edgeC = waitEdge(SKBO, t2, t3);
    }

    @Test
    void nuevo_baggage_esta_sin_asignar() {
        assertTrue(baggage.isUnassigned());
        assertTrue(baggage.getExpectedRoute().isEmpty());
        assertTrue(baggage.getRouteTraveled().isEmpty());
    }

    @Test
    void appendExpectedEdge_agrega_al_final_y_deja_asignado() {
        baggage.appendExpectedEdge(edgeA);
        baggage.appendExpectedEdge(edgeB);

        assertFalse(baggage.isUnassigned());
        assertEquals(2, baggage.getExpectedRoute().size());
        assertSame(edgeA, baggage.peekNextEdge());
    }

    @Test
    void confirmNextEdge_mueve_primer_edge_a_traveled() {
        baggage.appendExpectedEdge(edgeA);
        baggage.appendExpectedEdge(edgeB);

        baggage.confirmNextEdge();

        assertEquals(1, baggage.getExpectedRoute().size());
        assertEquals(1, baggage.getRouteTraveled().size());
        assertSame(edgeA, baggage.getRouteTraveled().get(0));
        assertSame(edgeB, baggage.peekNextEdge());
    }

    @Test
    void confirmNextEdge_en_ruta_vacia_no_lanza_excepcion() {
        assertDoesNotThrow(() -> baggage.confirmNextEdge());
        assertTrue(baggage.getRouteTraveled().isEmpty());
    }

    @Test
    void clearExpectedRoute_deja_baggage_sin_asignar() {
        baggage.appendExpectedEdge(edgeA);
        baggage.clearExpectedRoute();

        assertTrue(baggage.isUnassigned());
    }

    @Test
    void trimExpectedRouteFrom_0_limpia_todo() {
        baggage.appendExpectedEdge(edgeA);
        baggage.appendExpectedEdge(edgeB);
        baggage.appendExpectedEdge(edgeC);

        baggage.trimExpectedRouteFrom(0);

        assertTrue(baggage.isUnassigned());
    }

    @Test
    void trimExpectedRouteFrom_1_conserva_solo_el_primero() {
        baggage.appendExpectedEdge(edgeA);
        baggage.appendExpectedEdge(edgeB);
        baggage.appendExpectedEdge(edgeC);

        baggage.trimExpectedRouteFrom(1);

        assertEquals(1, baggage.getExpectedRoute().size());
        assertSame(edgeA, baggage.peekNextEdge());
    }

    @Test
    void getCurrentAirport_devuelve_icao_del_fromNode_del_currentEdge() {
        baggage.setCurrentEdge(edgeA);
        assertEquals("SKBO", baggage.getCurrentAirport());
    }

    @Test
    void getCurrentAirport_es_null_si_no_hay_currentEdge() {
        assertNull(baggage.getCurrentAirport());
    }

    @Test
    void isRouteComplete_true_cuando_ultimo_edge_llega_a_destino() {
        // destIcao del shipment S1 es SEQM
        WaitEdge edgeHaciaDestino = waitEdge(SEQM,
                Instant.parse("2026-01-02T12:00:00Z"),
                Instant.parse("2026-01-02T14:00:00Z"));
        baggage.appendExpectedEdge(edgeA);
        baggage.appendExpectedEdge(edgeHaciaDestino);

        assertTrue(baggage.isRouteComplete());
    }

    @Test
    void isRouteComplete_false_cuando_ultimo_edge_no_llega_a_destino() {
        baggage.appendExpectedEdge(edgeA); // edgeA.toNode es SKBO, no SEQM
        assertFalse(baggage.isRouteComplete());
    }

    @Test
    void peekNextEdge_no_remueve_el_edge() {
        baggage.appendExpectedEdge(edgeA);
        STEdge peeked = baggage.peekNextEdge();

        assertSame(edgeA, peeked);
        assertEquals(1, baggage.getExpectedRoute().size());
    }
}
