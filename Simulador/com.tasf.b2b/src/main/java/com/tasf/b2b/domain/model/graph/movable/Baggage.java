package com.tasf.b2b.domain.model.graph.movable;

import com.tasf.b2b.domain.model.graph.componentsgraph.STEdge;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Baggage {
    private final String id;
    private final Shipment shipment;
    private final String destIcao;
    private final Instant deadlineUtc;
    private List<STEdge>       routeTraveled;
    private ArrayDeque<STEdge> expectedRoute;
    private STEdge             currentEdge;

    // Constructor: llamado solo desde Shipment, index es 1-based
    public Baggage(Shipment shipment, int index) {
        this.id            = shipment.getShipmentData().getId() + "-B" + index;
        this.shipment      = shipment;
        this.destIcao      = shipment.getDestIcao();
        this.deadlineUtc   = shipment.getDeadlineUtc();
        this.routeTraveled = new ArrayList<>();
        this.expectedRoute = new ArrayDeque<>();
    }

    // Constructor de copia — solo para deepCopy en BaggageSolution
    Baggage(Baggage original) {
        this.id            = original.id;
        this.shipment      = original.shipment;
        this.destIcao      = original.destIcao;
        this.deadlineUtc   = original.deadlineUtc;
        this.currentEdge   = original.currentEdge;
        this.routeTraveled = new ArrayList<>(original.routeTraveled);
        this.expectedRoute = new ArrayDeque<>(original.expectedRoute);
    }

    // ── Setters ───────────────────────────────────────────────────────────────

    // Llamado por SpaceTimeGraph.addShipment (WaitEdge de entrada) y por
    // SimulationRunner en cada transición: WaitEdge→FlightEdge al partir,
    // FlightEdge→WaitEdge al llegar.
    public void setCurrentEdge(STEdge edge) {
        this.currentEdge = edge;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getId() { return id; }

    public Shipment getShipment() { return shipment; }

    public String getDestIcao() { return destIcao; }

    public Instant getDeadlineUtc() { return deadlineUtc; }

    public STEdge getCurrentEdge() { return currentEdge; }

    // Último aeropuerto físico conocido (fromNode de la arista actual)
    public String getCurrentAirport() {
        return currentEdge != null ? currentEdge.getFromNode().getIcao() : null;
    }

    public List<STEdge> getRouteTraveled() {
        return Collections.unmodifiableList(this.routeTraveled);
    }

    public List<STEdge> getExpectedRoute() {
        return Collections.unmodifiableList(new ArrayList<>(this.expectedRoute));
    }

    // Fuente de verdad: el baggage está sin ruta si y solo si expectedRoute está vacío
    public boolean isUnassigned() { return this.expectedRoute.isEmpty(); }

    // ── Gestión de ruta ───────────────────────────────────────────────────────

    // Borra la ruta planificada. currentEdge no cambia: el baggage sigue
    // físicamente en la misma arista, solo pierde su plan futuro.
    // SpaceTimeGraph.unassignBaggage libera las cargas de las aristas ANTES de llamar esto.
    public void clearExpectedRoute() {
        this.expectedRoute.clear();
    }

    public void setExpectedRoute(List<STEdge> route) {
        this.expectedRoute.clear();
        if (route != null && !route.isEmpty()) {
            for (STEdge edge : route) {
                this.expectedRoute.addLast(edge);
            }
        }
    }

    public void appendExpectedEdge(STEdge edge) {
        this.expectedRoute.addLast(edge);
    }

    // Nodo final esperado de la ruta planificada (null si sin ruta)
    public STEdge getExpectedDestEdge() {
        return this.expectedRoute.peekLast();
    }

    // Mueve el primer FlightEdge de expectedRoute a routeTraveled.
    // Llamado por SimulationRunner al procesar FlightArrivalEvent.
    // El runner actualiza currentEdge por separado (necesita el grafo para el WaitEdge).
    public void confirmNextEdge() {
        if (this.expectedRoute.isEmpty()) return;
        STEdge done = this.expectedRoute.pollFirst();
        this.routeTraveled.add(done);
    }

    // Peek del primer FlightEdge sin remover — usado por el runner al partir
    public STEdge peekNextEdge() {
        return this.expectedRoute.peekFirst();
    }

    // SpaceTimeGraph.unassignBaggageFrom libera las cargas de los edges eliminados ANTES de llamar esto.
    public void trimExpectedRouteFrom(int index) {
        if (index <= 0) {
            this.clearExpectedRoute();
            return;
        }
        List<STEdge> temp = new ArrayList<>(this.expectedRoute);
        temp.subList(index, temp.size()).clear();
        this.expectedRoute = new ArrayDeque<>(temp);
    }

    // ¿La ruta esperada llega al aeropuerto destino correcto?
    public boolean isRouteComplete() {
        STEdge last = this.getExpectedDestEdge();
        return last != null && last.getToNode().getIcao().equals(this.destIcao);
    }
}
