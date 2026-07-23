package com.tasf.b2b.domain.optimizer.alns;

import com.tasf.b2b.TestFixtures;
import com.tasf.b2b.domain.model.graph.componentsgraph.FlightEdge;
import com.tasf.b2b.domain.model.graph.componentsgraph.STNode;
import com.tasf.b2b.domain.model.graph.movable.Baggage;
import com.tasf.b2b.domain.model.graph.movable.Shipment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.tasf.b2b.TestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Cuándo queda lista una maleta para embarcar: sin margen al registrarse en su origen,
 * con margen de escala (minConnectionMinutes) una vez que ya voló algún tramo.
 */
class AlnsProjectionBuilderTest {

    private static final int CONNECT_MIN = 10;

    private static final Instant AHORA   = Instant.parse("2026-01-02T10:00:00Z");
    private static final Instant ATERRIZA = Instant.parse("2026-01-02T12:00:00Z");

    private Baggage baggage;

    @BeforeEach
    void setUp() {
        Shipment shipment = TestFixtures.shipment("S1", SKBO, EHAM,
                Instant.parse("2026-01-02T00:00:00Z"), 1);
        baggage = shipment.getBaggages().get(0);
    }

    // SEQM→EHAM aterrizando en ATERRIZA
    private static FlightEdge tramoQueAterrizaEn(Instant llegada) {
        STNode from = node(SEQM, llegada.minusSeconds(3600));
        STNode to   = node(EHAM, llegada);
        return new FlightEdge(schedSEQM_EHAM(), from, to);
    }

    @Test
    void maleta_recien_registrada_esta_lista_de_inmediato() {
        // Sin tramos recorridos y en tierra: es un envío nuevo en su aeropuerto de origen
        assertTrue(baggage.getRouteTraveled().isEmpty());

        Instant listo = AlnsProjectionBuilder.availableFrom(baggage, AHORA, CONNECT_MIN);

        assertEquals(AHORA, listo, "un envío recién registrado no debe esperar margen alguno");
    }

    @Test
    void maleta_en_vuelo_espera_el_margen_de_escala_tras_aterrizar() {
        baggage.setCurrentEdge(tramoQueAterrizaEn(ATERRIZA));

        Instant listo = AlnsProjectionBuilder.availableFrom(baggage, AHORA, CONNECT_MIN);

        assertEquals(ATERRIZA.plusSeconds(CONNECT_MIN * 60L), listo);
    }

    @Test
    void maleta_que_ya_aterrizo_y_espera_en_tierra_conserva_el_margen_de_escala() {
        // Ya voló un tramo y está en el almacén de la escala
        baggage.appendExpectedEdge(tramoQueAterrizaEn(ATERRIZA));
        baggage.confirmNextEdge();
        assertEquals(1, baggage.getRouteTraveled().size());

        // "Ahora" es justo después del aterrizaje: aún le corren los 10 min
        Instant justoDespues = ATERRIZA.plusSeconds(120);
        Instant listo = AlnsProjectionBuilder.availableFrom(baggage, justoDespues, CONNECT_MIN);

        assertEquals(ATERRIZA.plusSeconds(CONNECT_MIN * 60L), listo);
    }

    @Test
    void el_margen_de_escala_no_retrocede_en_el_tiempo() {
        baggage.appendExpectedEdge(tramoQueAterrizaEn(ATERRIZA));
        baggage.confirmNextEdge();

        // Pasó una hora desde el aterrizaje: el margen ya se cumplió, manda el reloj
        Instant muchoDespues = ATERRIZA.plusSeconds(3600);
        Instant listo = AlnsProjectionBuilder.availableFrom(baggage, muchoDespues, CONNECT_MIN);

        assertEquals(muchoDespues, listo);
    }
}
