package com.tasf.b2b.domain.model.graph;

import com.tasf.b2b.TestFixtures;
import com.tasf.b2b.domain.model.graph.componentsgraph.FlightEdge;
import com.tasf.b2b.domain.model.graph.componentsgraph.STEdge;
import com.tasf.b2b.domain.model.graph.componentsgraph.WaitEdge;
import com.tasf.b2b.domain.model.graph.immovable.FlightScheduleDataDTO;
import com.tasf.b2b.domain.model.graph.movable.Baggage;
import com.tasf.b2b.domain.model.graph.movable.Shipment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import static com.tasf.b2b.TestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class SpaceTimeGraphTest {

    // SKBO GMT-5, dep 08:00 local → 13:00 UTC
    private static final Instant DEP_JAN2_UTC = Instant.parse("2026-01-02T13:00:00Z");
    // SEQM GMT-5, arr 10:00 local → 15:00 UTC
    private static final Instant ARR_JAN2_UTC = Instant.parse("2026-01-02T15:00:00Z");
    private static final Instant DEP_JAN3_UTC = Instant.parse("2026-01-03T13:00:00Z");

    private static final Instant SIM_START = Instant.parse("2026-01-02T00:00:00Z");

    private SpaceTimeGraph graph;
    private FlightScheduleDataDTO sched;

    @BeforeEach
    void setUp() {
        graph = new SpaceTimeGraph();
        graph.addAirport(SKBO);
        graph.addAirport(SEQM);
        sched = schedSKBO_SEQM();
        graph.addScheduledFlight(sched);
    }

    // ── expandAllFlights ─────────────────────────────────────────────────────

    @Test
    void expandAllFlights_crea_flightEdges_para_cada_dia_del_horizonte() {
        List<FlightEdge> nuevos = graph.expandAllFlights(SIM_START);

        // Horizonte de 4 días: Jan 2, 3, 4, 5
        assertEquals(4, nuevos.size());
        assertTrue(nuevos.stream().allMatch(fe -> !fe.isCancelled()));
    }

    @Test
    void expandAllFlights_primer_edge_tiene_tiempos_UTC_correctos() {
        List<FlightEdge> nuevos = graph.expandAllFlights(SIM_START);

        FlightEdge fe = nuevos.stream()
                .filter(f -> f.getFromNode().getTimeUtc().equals(DEP_JAN2_UTC))
                .findFirst()
                .orElseThrow();

        assertEquals("SKBO", fe.getFromNode().getIcao());
        assertEquals("SEQM", fe.getToNode().getIcao());
        assertEquals(ARR_JAN2_UTC, fe.getToNode().getTimeUtc());
    }

    // ── expandSingleSchedule (alta de vuelo en sesión en curso) ──────────────

    @Test
    void expandSingleSchedule_materializa_instancias_dentro_del_horizonte_ya_expandido() {
        graph.addAirport(EHAM);
        graph.expandAllFlights(SIM_START);   // horizonte = Jan 2..5

        FlightScheduleDataDTO nuevo = schedSEQM_EHAM();
        List<FlightEdge> creados = graph.expandSingleSchedule(nuevo, SIM_START);

        // Mismo alcance que una expansión normal: un vuelo por día del horizonte
        assertEquals(4, creados.size());
        assertTrue(creados.stream().allMatch(fe -> fe.getFlightScheduleData().getId().equals(nuevo.getId())));
        assertTrue(creados.stream().allMatch(fe -> !fe.isCancelled()));
    }

    @Test
    void expandSingleSchedule_omite_las_salidas_anteriores_al_momento_actual() {
        graph.addAirport(EHAM);
        graph.expandAllFlights(SIM_START);

        // SEQM→EHAM sale 12:00 local (GMT-5) = 17:00 UTC. Si "ahora" es Jan 3 20:00 UTC,
        // las salidas de Jan 2 y Jan 3 ya pasaron: solo quedan Jan 4 y Jan 5.
        Instant ahora = Instant.parse("2026-01-03T20:00:00Z");
        List<FlightEdge> creados = graph.expandSingleSchedule(schedSEQM_EHAM(), ahora);

        assertEquals(2, creados.size());
        assertTrue(creados.stream().allMatch(fe -> !fe.getFromNode().getTimeUtc().isBefore(ahora)));
    }

    @Test
    void expandSingleSchedule_no_duplica_instancias_de_un_schedule_ya_expandido() {
        graph.expandAllFlights(SIM_START);

        // sched ya fue expandido por expandAllFlights: re-agregarlo no debe crear nada
        List<FlightEdge> creados = graph.expandSingleSchedule(sched, SIM_START);

        assertTrue(creados.isEmpty());
    }

    @Test
    void expandSingleSchedule_queda_registrado_para_las_proximas_expansiones() {
        graph.addAirport(EHAM);
        graph.expandAllFlights(SIM_START);

        FlightScheduleDataDTO nuevo = schedSEQM_EHAM();
        graph.expandSingleSchedule(nuevo, SIM_START);

        // Avanzar un día: la expansión normal debe seguir produciendo el vuelo nuevo
        List<FlightEdge> siguienteDia = graph.expandAllFlights(SIM_START.plusSeconds(86400));

        assertTrue(siguienteDia.stream()
                .anyMatch(fe -> fe.getFlightScheduleData().getId().equals(nuevo.getId())));
    }

    @Test
    void expandSingleSchedule_sin_expansion_previa_no_crea_nada_pero_registra() {
        graph.addAirport(EHAM);

        FlightScheduleDataDTO nuevo = schedSEQM_EHAM();
        List<FlightEdge> creados = graph.expandSingleSchedule(nuevo, SIM_START);

        // Sesión que aún no expandió: la primera expansión se encarga
        assertTrue(creados.isEmpty());

        List<FlightEdge> primeraExpansion = graph.expandAllFlights(SIM_START);
        assertTrue(primeraExpansion.stream()
                .anyMatch(fe -> fe.getFlightScheduleData().getId().equals(nuevo.getId())));
    }

    @Test
    void expandAllFlights_crea_waitEdge_entre_nodos_consecutivos_mismo_aeropuerto() {
        graph.expandAllFlights(SIM_START);

        // Debe haber un WaitEdge de SKBO Jan2 13:00 → SKBO Jan3 13:00
        STEdge waitEdge = graph.getWaitEdgeFrom(graph.getNode("SKBO", DEP_JAN2_UTC));

        assertNotNull(waitEdge);
        assertInstanceOf(WaitEdge.class, waitEdge);
        assertEquals(DEP_JAN3_UTC, waitEdge.getToNode().getTimeUtc());
    }

    @Test
    void expandAllFlights_segunda_llamada_no_duplica_edges() {
        List<FlightEdge> primera = graph.expandAllFlights(SIM_START);
        List<FlightEdge> segunda = graph.expandAllFlights(SIM_START);

        // La segunda expansión sobre el mismo rango no debe agregar nuevos edges
        assertTrue(segunda.isEmpty());
        assertEquals(4, primera.size());
    }

    @Test
    void expandAllFlights_devuelve_edges_para_registrar_eventos() {
        List<FlightEdge> nuevos = graph.expandAllFlights(SIM_START);
        assertFalse(nuevos.isEmpty());
        nuevos.forEach(fe -> {
            assertNotNull(fe.getFromNode());
            assertNotNull(fe.getToNode());
        });
    }

    // ── addShipment ──────────────────────────────────────────────────────────

    @Test
    void addShipment_agrega_baggages_a_pendingBaggages() {
        graph.expandAllFlights(SIM_START);
        Shipment s = shipment("S1", SKBO, SEQM, SIM_START, 3);

        graph.addShipment(s);

        assertEquals(3, graph.getPendingBaggages().size());
    }

    @Test
    void addShipment_asigna_currentEdge_a_waitEdge_en_nodo_entrada() {
        graph.expandAllFlights(SIM_START);
        Shipment s = shipment("S1", SKBO, SEQM, SIM_START, 1);

        graph.addShipment(s);

        Baggage b = s.getBaggages().get(0);
        assertNotNull(b.getCurrentEdge());
        assertInstanceOf(WaitEdge.class, b.getCurrentEdge());
        assertEquals("SKBO", b.getCurrentAirport());
    }

    @Test
    void addShipment_currentEdge_null_si_no_hay_nodo_en_aeropuerto_origen() {
        // No expandimos el grafo → no hay nodos
        Shipment s = shipment("S1", SKBO, SEQM, SIM_START, 1);

        graph.addShipment(s);

        assertNull(s.getBaggages().get(0).getCurrentEdge());
    }

    // ── assignBaggage / unassignBaggage ─────────────────────────────────────

    @Test
    void assignBaggage_mueve_de_pending_a_assigned() {
        graph.expandAllFlights(SIM_START);
        Shipment s = shipment("S1", SKBO, SEQM, SIM_START, 1);
        graph.addShipment(s);
        Baggage b = s.getBaggages().get(0);

        FlightEdge fe = flightEdgeJan2();
        b.appendExpectedEdge(fe);
        graph.assignBaggage(b);

        assertEquals(0, graph.getPendingBaggages().size());
        assertEquals(1, graph.getAssignedBaggages().size());
    }

    @Test
    void unassignBaggage_mueve_de_assigned_a_pending_y_limpia_ruta() {
        graph.expandAllFlights(SIM_START);
        Shipment s = shipment("S1", SKBO, SEQM, SIM_START, 1);
        graph.addShipment(s);
        Baggage b = s.getBaggages().get(0);

        FlightEdge fe = flightEdgeJan2();
        b.appendExpectedEdge(fe);
        graph.assignBaggage(b);
        graph.unassignBaggage(b);

        assertEquals(1, graph.getPendingBaggages().size());
        assertEquals(0, graph.getAssignedBaggages().size());
        assertTrue(b.isUnassigned());
    }

    // ── cancelFlight ─────────────────────────────────────────────────────────

    @Test
    void cancelFlight_marca_edge_cancelado_y_lo_saca_de_adyacencia() {
        graph.expandAllFlights(SIM_START);
        String key = sched.getId(); // "SKBO-SEQM-08:00"

        boolean result = graph.cancelFlight(key, DEP_JAN2_UTC);

        assertTrue(result);

        // Sigue "vivo" en RAM (getAllFlightEdges) para que la API/front lo pueda
        // seguir consultando y mostrar como cancelado — solo se saca de la
        // adyacencia para que deje de ser ruteable.
        FlightEdge edge = graph.getAllFlightEdges().stream()
                .filter(fe -> fe.getFromNode().getTimeUtc().equals(DEP_JAN2_UTC))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("El vuelo cancelado debería seguir en getAllFlightEdges"));
        assertTrue(edge.isCancelled());

        boolean enAdyacencia = graph.getEdgesFrom(edge.getFromNode()).contains(edge);
        assertFalse(enAdyacencia);
    }

    @Test
    void cancelFlight_devuelve_false_para_schedule_inexistente() {
        graph.expandAllFlights(SIM_START);

        boolean result = graph.cancelFlight("XX-YY-00:00", DEP_JAN2_UTC);

        assertFalse(result);
    }

    @Test
    void cancelFlight_devuelve_false_para_vuelo_ya_cancelado() {
        graph.expandAllFlights(SIM_START);
        String key = sched.getId();

        graph.cancelFlight(key, DEP_JAN2_UTC);
        boolean segundoIntento = graph.cancelFlight(key, DEP_JAN2_UTC);

        assertFalse(segundoIntento);
    }

    @Test
    void cancelFlight_encola_cancelacion_si_el_vuelo_esta_mas_alla_del_horizonte() {
        graph.expandAllFlights(SIM_START);
        // Jan 2 + 10 días → fuera del horizonte expandido (4 días)
        Instant futuro = Instant.parse("2026-01-12T13:00:00Z");

        boolean result = graph.cancelFlight(sched.getId(), futuro);

        // Devuelve true porque se acepta para procesar más tarde
        assertTrue(result);
    }

    // ── getBaggagesAffectedBy ────────────────────────────────────────────────

    @Test
    void getBaggagesAffectedBy_devuelve_baggages_con_ese_vuelo_en_ruta() {
        graph.expandAllFlights(SIM_START);
        Shipment s = shipment("S1", SKBO, SEQM, SIM_START, 2);
        graph.addShipment(s);

        FlightEdge fe = flightEdgeJan2();
        // Solo el primer baggage tiene ese vuelo en su ruta
        Baggage afectado = s.getBaggages().get(0);
        afectado.appendExpectedEdge(fe);
        graph.assignBaggage(afectado);

        List<Baggage> afectados = graph.getBaggagesAffectedBy(sched.getId(), DEP_JAN2_UTC);

        assertEquals(1, afectados.size());
        assertSame(afectado, afectados.get(0));
    }

    @Test
    void getBaggagesAffectedBy_devuelve_vacio_si_ninguno_usa_ese_vuelo() {
        graph.expandAllFlights(SIM_START);
        Shipment s = shipment("S1", SKBO, SEQM, SIM_START, 1);
        graph.addShipment(s);

        List<Baggage> afectados = graph.getBaggagesAffectedBy(sched.getId(), DEP_JAN2_UTC);

        assertTrue(afectados.isEmpty());
    }

    // ── getWaitEdgeFrom ──────────────────────────────────────────────────────

    @Test
    void getWaitEdgeFrom_devuelve_null_para_ultimo_nodo_de_la_timeline() {
        graph.expandAllFlights(SIM_START);
        // El último nodo de SKBO (Jan 5 13:00 UTC) no tiene sucesor en SKBO
        Instant ultimoDia = Instant.parse("2026-01-05T13:00:00Z");
        var nodo = graph.getNode("SKBO", ultimoDia);

        STEdge resultado = graph.getWaitEdgeFrom(nodo);

        assertNull(resultado);
    }

    // ── helper ───────────────────────────────────────────────────────────────

    private FlightEdge flightEdgeJan2() {
        return graph.getAllFlightEdges().stream()
                .filter(fe -> fe.getFromNode().getTimeUtc().equals(DEP_JAN2_UTC))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("FlightEdge Jan2 no encontrado"));
    }
}
