package com.tasf.b2b;

import com.tasf.b2b.domain.model.graph.componentsgraph.STNode;
import com.tasf.b2b.domain.model.graph.componentsgraph.WaitEdge;
import com.tasf.b2b.domain.model.graph.immovable.*;
import com.tasf.b2b.domain.model.graph.movable.Shipment;

import java.time.Instant;
import java.time.LocalTime;

public final class TestFixtures {

    public static final DeliveryTypeValues DELIVERY_TYPES = new DeliveryTypeValues();

    // Mismo continente → INTRACONTINENTAL (12 h)
    public static final AirportDataDTO SKBO = airport("SKBO", "America", -5, 200);
    public static final AirportDataDTO SEQM = airport("SEQM", "America", -5, 150);
    // Distinto continente → INTERCONTINENTAL (24 h)
    public static final AirportDataDTO EHAM = airport("EHAM", "Europe",   1, 500);

    // SKBO→SEQM: dep 08:00 local (GMT-5) = 13:00 UTC, arr 10:00 local = 15:00 UTC, cap 50
    public static FlightScheduleDataDTO schedSKBO_SEQM() {
        return new FlightScheduleDataDTO(SKBO, SEQM,
                LocalTime.of(8, 0), LocalTime.of(10, 0), 50);
    }

    // SEQM→EHAM: dep 12:00 local (GMT-5) = 17:00 UTC, arr 06:00 local (GMT+1) = 05:00 UTC → +1 day
    public static FlightScheduleDataDTO schedSEQM_EHAM() {
        return new FlightScheduleDataDTO(SEQM, EHAM,
                LocalTime.of(12, 0), LocalTime.of(6, 0), 30);
    }

    public static AirportDataDTO airport(String icao, String continent, int gmt, int capacity) {
        return new AirportDataDTO(icao, "City", "Country", continent, "short",
                gmt, capacity, 0.0, 0.0);
    }

    public static ShipmentDataDTO shipmentData(String id, AirportDataDTO orig, AirportDataDTO dest,
                                               Instant entry, int qty) {
        return new ShipmentDataDTO(id, entry, orig, dest, qty, "client1", DELIVERY_TYPES);
    }

    public static Shipment shipment(String id, AirportDataDTO orig, AirportDataDTO dest,
                                    Instant entry, int qty) {
        return new Shipment(shipmentData(id, orig, dest, entry, qty));
    }

    public static STNode node(AirportDataDTO airport, Instant time) {
        return new STNode(airport, time);
    }

    public static WaitEdge waitEdge(AirportDataDTO airport, Instant from, Instant to) {
        return new WaitEdge(airport, node(airport, from), node(airport, to));
    }
}
