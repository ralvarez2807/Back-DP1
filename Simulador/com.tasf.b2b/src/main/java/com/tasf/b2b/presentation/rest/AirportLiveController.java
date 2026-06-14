package com.tasf.b2b.presentation.rest;

import com.tasf.b2b.application.dto.AirportLiveView;
import com.tasf.b2b.application.port.in.SimulationQueryPort;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/simulations")
public class AirportLiveController {

    private final SimulationQueryPort queryPort;

    public AirportLiveController(SimulationQueryPort queryPort) {
        this.queryPort = queryPort;
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    record AirportItem(
            String  icao,
            String  city,
            String  continent,
            int     load,
            int     capacity,
            double  occupancyPct,
            String  occupancyLevel) {

        static AirportItem from(AirportLiveView v) {
            return new AirportItem(v.icao(), v.city(), v.continent(),
                    v.load(), v.capacity(), v.occupancyPct(), v.occupancyLevel());
        }
    }

    record InboundFlightResponse(
            String       flightId,
            String       fromIcao,
            Instant      arrTime,
            int          baggageCount,
            List<String> shipmentIds) {

        static InboundFlightResponse from(AirportLiveView.InboundFlight f) {
            return new InboundFlightResponse(f.flightId(), f.fromIcao(), f.arrTime(),
                    f.baggageCount(), f.shipmentIds());
        }
    }

    record InboundResponse(
            String                      icao,
            Instant                     simTime,
            List<InboundFlightResponse> inbound) {

        static InboundResponse from(AirportLiveView.InboundView v) {
            return new InboundResponse(v.icao(), v.simTime(),
                    v.inbound().stream().map(InboundFlightResponse::from).toList());
        }
    }

    record OutboundFlightResponse(
            String       flightId,
            String       toIcao,
            Instant      depTime,
            int          baggageCount,
            List<String> shipmentIds) {

        static OutboundFlightResponse from(AirportLiveView.OutboundFlight f) {
            return new OutboundFlightResponse(f.flightId(), f.toIcao(), f.depTime(),
                    f.baggageCount(), f.shipmentIds());
        }
    }

    record OutboundResponse(
            String                       icao,
            Instant                      simTime,
            List<OutboundFlightResponse> outbound) {

        static OutboundResponse from(AirportLiveView.OutboundView v) {
            return new OutboundResponse(v.icao(), v.simTime(),
                    v.outbound().stream().map(OutboundFlightResponse::from).toList());
        }
    }

    record TransitBaggageResponse(
            String  baggageId,
            String  shipmentId,
            String  destIcao,
            Instant deadlineUtc,
            String  nextFlightId,
            Instant nextDepTime) {

        static TransitBaggageResponse from(AirportLiveView.TransitBaggage b) {
            return new TransitBaggageResponse(b.baggageId(), b.shipmentId(), b.destIcao(),
                    b.deadlineUtc(), b.nextFlightId(), b.nextDepTime());
        }
    }

    record TransitResponse(
            String                       icao,
            Instant                      simTime,
            List<TransitBaggageResponse> transit) {

        static TransitResponse from(AirportLiveView.TransitView v) {
            return new TransitResponse(v.icao(), v.simTime(),
                    v.transit().stream().map(TransitBaggageResponse::from).toList());
        }
    }

    // ── Endpoints ─────────────────────────────────────────────────────────────

    @GetMapping("/{id}/airports")
    public List<AirportItem> getAirports(@PathVariable String id) {
        return queryPort.getAirports(id).stream()
                .map(AirportItem::from)
                .toList();
    }

    @GetMapping("/{id}/airports/{icao}/inbound")
    public InboundResponse getInbound(
            @PathVariable String id,
            @PathVariable String icao) {
        return InboundResponse.from(queryPort.getAirportInbound(id, icao));
    }

    @GetMapping("/{id}/airports/{icao}/outbound")
    public OutboundResponse getOutbound(
            @PathVariable String id,
            @PathVariable String icao) {
        return OutboundResponse.from(queryPort.getAirportOutbound(id, icao));
    }

    @GetMapping("/{id}/airports/{icao}/transit")
    public TransitResponse getTransit(
            @PathVariable String id,
            @PathVariable String icao) {
        return TransitResponse.from(queryPort.getAirportTransit(id, icao));
    }
}
