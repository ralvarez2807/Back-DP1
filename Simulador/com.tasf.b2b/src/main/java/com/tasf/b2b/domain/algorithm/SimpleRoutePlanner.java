// SimpleRoutePlanner.java - Alternativa más práctica
package com.tasf.b2b.domain.algorithm;

import com.tasf.b2b.domain.model.graph.SpaceTimeGraph;
import com.tasf.b2b.domain.model.graph.componentsgraph.STEdge;
import com.tasf.b2b.domain.model.graph.componentsgraph.FlightEdge;
import com.tasf.b2b.domain.model.graph.componentsgraph.STNode;
import com.tasf.b2b.domain.model.graph.movable.Baggage;

import java.time.Instant;
import java.util.*;

public class SimpleRoutePlanner {

    private final SpaceTimeGraph graph;

    public SimpleRoutePlanner(SpaceTimeGraph graph) {
        this.graph = graph;
    }

    public List<STEdge> findRoute(Baggage baggage) {
        String destIcao = baggage.getDestIcao();
        STNode startNode = graph.resolveEntryNode(baggage.getShipment().getShipmentData());

        if (startNode == null) return new ArrayList<>();

        // BFS para encontrar la ruta más temprana
        return bfsShortestRoute(startNode, destIcao);
    }

    private List<STEdge> bfsShortestRoute(STNode start, String destIcao) {
        Queue<NodePath> queue = new LinkedList<>();
        Set<STNode> visited = new HashSet<>();

        queue.offer(new NodePath(start, new ArrayList<>()));
        visited.add(start);

        while (!queue.isEmpty()) {
            NodePath current = queue.poll();
            STNode node = current.node;

            // Llegamos al destino?
            if (node.getAirport().getIcao().equals(destIcao)) {
                return current.edges;
            }

            // Explorar aristas salientes
            List<STEdge> edges = graph.getEdgesFrom(node);
            if (edges == null) continue;

            for (STEdge edge : edges) {
                STNode nextNode = edge.getToNode();

                // Filtrar vuelos cancelados o llenos
                if (edge instanceof FlightEdge flight) {
                    if (flight.isCancelled() || flight.getRemainingCapacity() <= 0) {
                        continue;
                    }
                }

                if (!visited.contains(nextNode)) {
                    visited.add(nextNode);
                    List<STEdge> newEdges = new ArrayList<>(current.edges);
                    newEdges.add(edge);
                    queue.offer(new NodePath(nextNode, newEdges));
                }
            }
        }

        return new ArrayList<>(); // No se encontró ruta
    }

    private static class NodePath {
        STNode node;
        List<STEdge> edges;

        NodePath(STNode node, List<STEdge> edges) {
            this.node = node;
            this.edges = edges;
        }
    }
}