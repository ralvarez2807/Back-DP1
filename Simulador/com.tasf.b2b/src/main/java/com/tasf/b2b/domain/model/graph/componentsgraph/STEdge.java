// STEdge.java
package com.tasf.b2b.domain.model.graph.componentsgraph;

import java.time.Duration;

public abstract class STEdge {
    //Conecta dos aeropuertos en tiempos distintos por vuelo o porque no hay vuelo en el mismo aeropuerto
    protected final STNode fromNode;
    protected final STNode toNode;

    //Constructor
    public STEdge(STNode fromNode, STNode toNode) {
        this.fromNode     = fromNode;
        this.toNode       = toNode;
    }

    //Getters
    public STNode getFromNode() {
        return fromNode;
    }

    public STNode getToNode() {
        return toNode;
    }

    // Minutos entre nodo origen y nodo destino de esta arista
    public long costMinutes() {
        return Duration.between(this.fromNode.getTimeUtc(), this.toNode.getTimeUtc()).toMinutes();
    }

    public abstract int getRemainingCapacity();
    public abstract void assign();
    public abstract void release();
}