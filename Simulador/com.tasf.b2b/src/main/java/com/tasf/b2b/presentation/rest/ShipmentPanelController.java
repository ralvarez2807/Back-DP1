package com.tasf.b2b.presentation.rest;

import com.tasf.b2b.application.dto.ShipmentDiagnosticsView;
import com.tasf.b2b.application.dto.ShipmentView;
import com.tasf.b2b.application.port.in.SimulationQueryPort;
import com.tasf.b2b.domain.simulator.dto.SlaBreachInfo;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/simulations")
public class ShipmentPanelController {

    private final SimulationQueryPort queryPort;

    public ShipmentPanelController(SimulationQueryPort queryPort) {
        this.queryPort = queryPort;
    }

    // ── DTOs de respuesta ─────────────────────────────────────────────────────

    record ShipmentListItem(
            String  shipmentId,
            String  originIcao,
            String  destIcao,
            Instant deadlineUtc,
            int     totalBaggages,
            int     delivered,
            int     noRoute,
            int     onTime,
            int     late,
            int     breached) {

        static ShipmentListItem from(ShipmentView v) {
            return new ShipmentListItem(v.shipmentId(), v.originIcao(), v.destIcao(),
                    v.deadlineUtc(), v.totalBaggages(), v.delivered(),
                    v.noRoute(), v.onTime(), v.late(), v.breached());
        }
    }

    record LegResponse(
            String  fromIcao,
            String  toIcao,
            Instant depTime,
            Instant arrTime,
            String  state) {

        static LegResponse from(ShipmentView.Leg l) {
            return new LegResponse(l.fromIcao(), l.toIcao(), l.depTime(), l.arrTime(), l.state());
        }
    }

    record BaggageDetailResponse(
            String          baggageId,
            String          status,
            String          currentIcao,
            Instant         estimatedArrival,
            Boolean         onTime,
            List<LegResponse> route) {

        static BaggageDetailResponse from(ShipmentView.BaggageDetail b) {
            return new BaggageDetailResponse(b.baggageId(), b.status(), b.currentIcao(),
                    b.estimatedArrival(), b.onTime(),
                    b.route().stream().map(LegResponse::from).toList());
        }
    }

    record ShipmentDetailResponse(
            String                      shipmentId,
            String                      originIcao,
            String                      destIcao,
            Instant                     deadlineUtc,
            int                         totalBaggages,
            List<BaggageDetailResponse> baggages) {

        static ShipmentDetailResponse from(ShipmentView.DetailView v) {
            return new ShipmentDetailResponse(v.shipmentId(), v.originIcao(), v.destIcao(),
                    v.deadlineUtc(), v.totalBaggages(),
                    v.baggages().stream().map(BaggageDetailResponse::from).toList());
        }
    }

    // ── Endpoints ─────────────────────────────────────────────────────────────

    @GetMapping("/{id}/shipments")
    public List<ShipmentListItem> getShipments(@PathVariable String id) {
        return queryPort.getShipments(id).stream()
                .map(ShipmentListItem::from)
                .toList();
    }

    @GetMapping("/{id}/shipments/{shipmentId}")
    public ShipmentDetailResponse getShipmentDetail(
            @PathVariable String id,
            @PathVariable String shipmentId) {
        return ShipmentDetailResponse.from(queryPort.getShipmentDetail(id, shipmentId));
    }

    // Forense de por qué un envío incumplió SLA / quedó sin ruta.
    @GetMapping("/{id}/shipments/{shipmentId}/diagnostics")
    public ShipmentDiagnosticsView getShipmentDiagnostics(
            @PathVariable String id,
            @PathVariable String shipmentId) {
        return queryPort.getShipmentDiagnostics(id, shipmentId);
    }

    // Foto forense de cada incumplimiento de SLA en el instante en que ocurrió.
    @GetMapping("/{id}/sla-breaches")
    public List<SlaBreachInfo> getSlaBreaches(@PathVariable String id) {
        return queryPort.getSlaBreaches(id);
    }
}
