package com.tasf.b2b.presentation.rest;

import com.tasf.b2b.application.dto.ReportView;
import com.tasf.b2b.application.port.in.SimulationQueryPort;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/simulations")
public class ReportController {

    private final SimulationQueryPort queryPort;

    public ReportController(SimulationQueryPort queryPort) {
        this.queryPort = queryPort;
    }

    record TopRouteResponse(String fromIcao, String toIcao, int count) {
        static TopRouteResponse from(ReportView.TopRoute r) {
            return new TopRouteResponse(r.fromIcao(), r.toIcao(), r.count());
        }
    }

    record ReportResponse(
            Instant                  simStart,
            Instant                  simEnd,
            String                   status,
            int                      totalShipments,
            int                      totalBaggages,
            int                      delivered,
            int                      slaBreaches,
            int                      pending,
            int                      inFlight,
            double                   throughputPerHour,
            List<TopRouteResponse>   topRoutes) {

        static ReportResponse from(ReportView v) {
            return new ReportResponse(
                    v.simStart(), v.simEnd(), v.status(),
                    v.totalShipments(), v.totalBaggages(), v.delivered(),
                    v.slaBreaches(), v.pending(), v.inFlight(),
                    v.throughputPerHour(),
                    v.topRoutes().stream().map(TopRouteResponse::from).toList());
        }
    }

    @GetMapping("/{id}/reports/summary")
    public ReportResponse getSummary(@PathVariable String id) {
        return ReportResponse.from(queryPort.getReport(id));
    }
}
