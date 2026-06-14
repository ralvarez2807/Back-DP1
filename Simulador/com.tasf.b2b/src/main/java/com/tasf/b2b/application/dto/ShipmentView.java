package com.tasf.b2b.application.dto;

import java.time.Instant;
import java.util.List;

public record ShipmentView(
        String  shipmentId,
        String  originIcao,
        String  destIcao,
        Instant deadlineUtc,
        int     totalBaggages,
        int     delivered,
        int     noRoute,
        int     onTime,
        int     late) {

    public record Leg(
            String  fromIcao,
            String  toIcao,
            Instant depTime,
            Instant arrTime,
            String  state) {}   // TRAVELED | IN_FLIGHT | PLANNED

    public record BaggageDetail(
            String    baggageId,
            String    status,           // PENDING | WAITING | IN_FLIGHT | DELIVERED
            String    currentIcao,
            Instant   estimatedArrival, // null si sin ruta completa o entregado
            Boolean   onTime,           // null si sin ruta completa o entregado
            List<Leg> route) {}

    public record DetailView(
            String              shipmentId,
            String              originIcao,
            String              destIcao,
            Instant             deadlineUtc,
            int                 totalBaggages,
            List<BaggageDetail> baggages) {}
}
