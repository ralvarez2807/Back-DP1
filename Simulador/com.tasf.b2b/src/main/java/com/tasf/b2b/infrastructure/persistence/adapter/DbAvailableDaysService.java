package com.tasf.b2b.infrastructure.persistence.adapter;

import com.tasf.b2b.application.port.in.AvailableDaysPort;
import com.tasf.b2b.infrastructure.persistence.repository.SimulationShipmentJpaRepository;

import java.time.LocalDate;
import java.util.List;

public class DbAvailableDaysService implements AvailableDaysPort {

    private final List<LocalDate> dates;

    public DbAvailableDaysService(SimulationShipmentJpaRepository repo) {
        this.dates = repo.findDistinctDates();
    }

    @Override
    public List<LocalDate> getAvailableDates() {
        return dates;
    }
}
