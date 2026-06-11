// STNode.java
package com.tasf.b2b.domain.model.graph.componentsgraph;

import com.tasf.b2b.domain.model.graph.immovable.AirportDataDTO;

import java.time.Instant;
import java.util.Objects;

public class STNode {
    private final String         icao;
    private final AirportDataDTO airport;
    private final Instant        timeUtc;

    //Constructor
    public STNode(AirportDataDTO airport, Instant timeUtc) {
        this.icao    = airport.getIcao();
        this.airport = airport;
        this.timeUtc = timeUtc;
    }

    //Getters
    public String         getIcao()    { return icao; }
    public AirportDataDTO getAirport() { return airport; }
    public Instant        getTimeUtc() { return timeUtc; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof STNode)) return false;
        STNode n = (STNode) o;
        return Objects.equals(airport, n.airport) &&
               Objects.equals(timeUtc, n.timeUtc);
    }

    @Override
    public int hashCode() { return Objects.hash(airport, timeUtc); }

    @Override
    public String toString() { return airport + "@" + timeUtc + " UTC"; }
}
