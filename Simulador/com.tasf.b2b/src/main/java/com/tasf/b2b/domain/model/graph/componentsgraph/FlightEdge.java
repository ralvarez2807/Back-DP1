// FlightEdge.java
package com.tasf.b2b.domain.model.graph.componentsgraph;

import com.tasf.b2b.domain.model.graph.immovable.FlightScheduleDataDTO;
import com.tasf.b2b.domain.util.TimeUtils;

import java.time.format.DateTimeFormatter;

public class FlightEdge extends STEdge {
    private final FlightScheduleDataDTO flightScheduleData;
    private final String idFlightEdge;
    private boolean cancelled;
    private final int capacity;
    private int       load;

    //Constructor
    public FlightEdge(FlightScheduleDataDTO flightScheduleData, STNode fromNode, STNode toNode) {
        super(fromNode, toNode);
        //Formateador
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");


        this.flightScheduleData = flightScheduleData;
        this.idFlightEdge = flightScheduleData.getId() + "-" + TimeUtils.utcToLocal(fromNode.getTimeUtc(), fromNode.getAirport().getGmtOffset()).format(formatter);
        this.cancelled = false;
        this.capacity = flightScheduleData.getCapacity();
        this.load = 0;
    }

    //Setters
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public void setLoad(int load) {
        this.load = load;
    }

    //Getters
    public FlightScheduleDataDTO getFlightScheduleData() {
        return flightScheduleData;
    }

    public String getIdFlightEdge() {
        return idFlightEdge;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getLoad() {
        return load;
    }

    @Override
    public void assign()  { this.load += 1; }
    @Override
    public void release() { this.load -= 1; }

    //Abstractos
    @Override
    public int getRemainingCapacity(){
        return this.capacity - this.load;
    }

}