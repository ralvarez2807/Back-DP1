// WaitEdge.java
package com.tasf.b2b.domain.model.graph.componentsgraph;

import com.tasf.b2b.domain.model.graph.immovable.AirportDataDTO;

public class WaitEdge extends STEdge {
    private final AirportDataDTO airportData;
    private final int capacity;
    private int dynamicLoad; // baggages actualmente esperando en este tramo (fuente de verdad: runner)

    //Constructor
    public WaitEdge(AirportDataDTO airportData, STNode fromNode, STNode toNode) {
        super(fromNode, toNode);
        this.airportData = airportData;
        this.capacity    = airportData.getCapacity();
        this.dynamicLoad = 0;
    }

    //Getters
    public AirportDataDTO getAirportData() { return airportData; }

    public int getCapacity() { return capacity; }

    public int getDynamicLoad() { return dynamicLoad; }

    @Override
    public void assign()  { this.dynamicLoad += 1; }

    @Override
    public void release() { this.dynamicLoad -= 1; }

    @Override
    public int getRemainingCapacity() { return this.capacity - this.dynamicLoad; }
}
