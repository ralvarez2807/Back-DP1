package com.tasf.b2b.presentation.rest;

import com.tasf.b2b.application.dto.FlightView;
import com.tasf.b2b.application.port.in.SimulationQueryPort;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/simulations")
public class FlightsController {

    private final SimulationQueryPort queryPort;

    public FlightsController(SimulationQueryPort queryPort) {
        this.queryPort = queryPort;
    }

    // ── DTOs de respuesta ─────────────────────────────────────────────────────

    record FlightListItem(
            String  flightId,
            String  fromIcao,
            String  toIcao,
            Instant depTime,
            Instant arrTime,
            String  status,
            int     load,
            int     capacity,
            double  occupancyPct,
            String  occupancyLevel) {

        static FlightListItem from(FlightView v) {
            return new FlightListItem(v.flightId(), v.fromIcao(), v.toIcao(),
                    v.depTime(), v.arrTime(), v.status(), v.load(), v.capacity(),
                    v.occupancyPct(), v.occupancyLevel());
        }
    }

    record BaggageItem(
            String  baggageId,
            String  destIcao,
            Instant deadlineUtc) {

        static BaggageItem from(FlightView.BaggageOnFlight b) {
            return new BaggageItem(b.baggageId(), b.destIcao(), b.deadlineUtc());
        }
    }

    record ShipmentItem(
            String            shipmentId,
            String            originIcao,
            String            destIcao,
            int               baggageCount,
            List<BaggageItem> baggages) {

        static ShipmentItem from(FlightView.ShipmentOnFlight s) {
            return new ShipmentItem(s.shipmentId(), s.originIcao(), s.destIcao(),
                    s.baggageCount(),
                    s.baggages().stream().map(BaggageItem::from).toList());
        }
    }

    record FlightDetailItem(
            String            flightId,
            String            fromIcao,
            String            toIcao,
            Instant           depTime,
            Instant           arrTime,
            String            status,
            int               load,
            int               capacity,
            double            occupancyPct,
            String            occupancyLevel,
            List<ShipmentItem> shipments) {

        static FlightDetailItem from(FlightView.DetailView v) {
            return new FlightDetailItem(v.flightId(), v.fromIcao(), v.toIcao(),
                    v.depTime(), v.arrTime(), v.status(), v.load(), v.capacity(),
                    v.occupancyPct(), v.occupancyLevel(),
                    v.shipments().stream().map(ShipmentItem::from).toList());
        }
    }

    // ── Endpoints ─────────────────────────────────────────────────────────────

    @GetMapping("/{id}/flights")
    public List<FlightListItem> getFlights(@PathVariable String id) {
        return queryPort.getFlights(id).stream()
                .map(FlightListItem::from)
                .toList();
    }

    @GetMapping("/{id}/flights/{flightId}")
    public FlightDetailItem getFlightDetail(
            @PathVariable String id,
            @PathVariable String flightId) {
        return FlightDetailItem.from(queryPort.getFlightDetail(id, flightId));
    }
}
