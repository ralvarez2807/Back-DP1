// GeneticAlgorithm.java - Versión simplificada para integrar con tu sistema
package com.tasf.b2b.domain.algorithm;

import com.tasf.b2b.domain.model.graph.SpaceTimeGraph;
import com.tasf.b2b.domain.model.graph.componentsgraph.STEdge;
import com.tasf.b2b.domain.model.graph.componentsgraph.FlightEdge;
import com.tasf.b2b.domain.model.graph.movable.Baggage;
import com.tasf.b2b.domain.model.graph.componentsgraph.STNode;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Algoritmo Genético para planificación de rutas de carga aérea
 * Optimiza: tiempo de entrega, uso de capacidad, cumplimiento de deadlines
 */
public class GeneticAlgorithm {

    // Parámetros del GA
    private static final int POPULATION_SIZE = 50;
    private static final int MAX_GENERATIONS = 100;
    private static final double MUTATION_RATE = 0.15;
    private static final double ELITISM_RATE = 0.10;
    private static final int TOURNAMENT_SIZE = 3;

    private final SpaceTimeGraph graph;
    private final Random random;

    public GeneticAlgorithm(SpaceTimeGraph graph) {
        this.graph = graph;
        this.random = ThreadLocalRandom.current();
    }

    /**
     * Planifica ruta óptima para un baggage específico
     */
    public List<STEdge> planRoute(Baggage baggage, Instant startTime) {
        String destIcao = baggage.getDestIcao();

        STNode startNode = graph.resolveEntryNode(baggage.getShipment().getShipmentData());
        if (startNode == null) {
            return new ArrayList<>();
        }

        List<STEdge> bestRoute = null;

        // 1. Intentar vuelo directo
        bestRoute = findDirectFlight(startNode, destIcao);
        if (isValidRoute(bestRoute, destIcao)) {
            return bestRoute;
        }

        // 2. Intentar ruta más rápida
        bestRoute = findFastestRoute(startNode, destIcao);
        if (isValidRoute(bestRoute, destIcao)) {
            return bestRoute;
        }

        // 3. Intentar BFS normal
        bestRoute = findRouteBFS(startNode, destIcao);
        if (isValidRoute(bestRoute, destIcao)) {
            return bestRoute;
        }

        // 4. Si nada funciona, intentar GA
        List<Chromosome> population = initializePopulation(baggage, startTime);
        if (!population.isEmpty()) {
            for (int generation = 0; generation < MAX_GENERATIONS && generation < 10; generation++) {
                for (Chromosome chromosome : population) {
                    chromosome.fitness = evaluateFitness(chromosome, baggage);
                }
                population.sort((a, b) -> Double.compare(b.fitness, a.fitness));

                if (isValidRoute(population.get(0).edges, destIcao)) {
                    return population.get(0).edges;
                }

                // Evolucionar...
                List<Chromosome> newPopulation = new ArrayList<>();
                int eliteCount = Math.max(1, (int)(POPULATION_SIZE * ELITISM_RATE));
                for (int i = 0; i < eliteCount; i++) {
                    newPopulation.add(population.get(i));
                }
                while (newPopulation.size() < POPULATION_SIZE) {
                    Chromosome parent1 = tournamentSelect(population);
                    Chromosome parent2 = tournamentSelect(population);
                    Chromosome child = crossover(parent1, parent2);
                    mutate(child);
                    newPopulation.add(child);
                }
                population = newPopulation;
            }

            if (isValidRoute(population.get(0).edges, destIcao)) {
                return population.get(0).edges;
            }
        }

        System.err.println("[ERROR] No se encontró ruta válida para " + baggage.getId() +
                " desde " + startNode.getAirport().getIcao() + " hasta " + destIcao);
        return new ArrayList<>();
    }

    // Método auxiliar para validar ruta
    private boolean isValidRoute(List<STEdge> route, String destIcao) {
        if (route == null || route.isEmpty()) return false;
        STEdge lastEdge = route.get(route.size() - 1);
        return lastEdge.getToNode().getAirport().getIcao().equals(destIcao);
    }

    // Método auxiliar para encontrar vuelo directo
    private List<STEdge> findDirectFlight(STNode startNode, String destIcao) {
        List<STEdge> edges = graph.getEdgesFrom(startNode);
        if (edges == null) return null;

        for (STEdge edge : edges) {
            if (edge instanceof FlightEdge &&
                    edge.getToNode().getAirport().getIcao().equals(destIcao)) {
                List<STEdge> route = new ArrayList<>();
                route.add(edge);
                return route;
            }
        }
        return null;
    }

    /**
     * Búsqueda DFS para encontrar ruta al destino
     */
    private List<STEdge> findRouteBFS(STNode startNode, String destIcao) {
        // Cola para BFS: cada elemento es una ruta (lista de edges)
        Queue<List<STEdge>> queue = new LinkedList<>();
        Set<STNode> visited = new HashSet<>();

        // Iniciar con rutas vacías desde el nodo startNode
        queue.offer(new ArrayList<>());
        visited.add(startNode);

        while (!queue.isEmpty()) {
            List<STEdge> currentPath = queue.poll();
            STNode currentNode = currentPath.isEmpty() ? startNode :
                    currentPath.get(currentPath.size() - 1).getToNode();

            // Llegamos al destino?
            if (currentNode.getAirport().getIcao().equals(destIcao)) {
                return currentPath;
            }

            // Explorar aristas salientes
            List<STEdge> edges = graph.getEdgesFrom(currentNode);
            if (edges == null) continue;

            for (STEdge edge : edges) {
                STNode nextNode = edge.getToNode();

                // Filtrar solo vuelos no cancelados
                if (edge instanceof FlightEdge flight) {
                    if (flight.isCancelled()) continue;
                }

                if (!visited.contains(nextNode)) {
                    visited.add(nextNode);
                    List<STEdge> newPath = new ArrayList<>(currentPath);
                    newPath.add(edge);
                    queue.offer(newPath);
                }
            }
        }

        return null; // No se encontró ruta
    }
    private List<STEdge> findFastestRoute(STNode startNode, String destIcao) {
        Queue<RouteNode> queue = new LinkedList<>();
        Map<STNode, Instant> bestArrivalTime = new HashMap<>();

        queue.offer(new RouteNode(startNode, new ArrayList<>(), startNode.getTimeUtc()));
        bestArrivalTime.put(startNode, startNode.getTimeUtc());

        List<STEdge> bestRoute = null;
        Instant bestArrival = null;

        while (!queue.isEmpty()) {
            RouteNode current = queue.poll();

            // ✅ Verificar si llegamos al destino
            if (current.node.getAirport().getIcao().equals(destIcao)) {
                if (bestArrival == null || current.arrivalTime.isBefore(bestArrival)) {
                    bestArrival = current.arrivalTime;
                    bestRoute = current.route;
                }
                // ✅ Continuar buscando rutas más rápidas (no detenerse)
                continue;
            }

            List<STEdge> edges = graph.getEdgesFrom(current.node);
            if (edges == null) continue;

            for (STEdge edge : edges) {
                if (edge instanceof FlightEdge flight) {
                    if (flight.isCancelled()) continue;

                    Instant edgeDeparture = edge.getFromNode().getTimeUtc();
                    Instant edgeArrival = edge.getToNode().getTimeUtc();

                    long waitHours = java.time.Duration.between(current.arrivalTime, edgeDeparture).toHours();
                    if (waitHours < 0) continue;
                    if (waitHours > 48) continue; // No esperar más de 48 horas

                    STNode nextNode = edge.getToNode();
                    Instant newArrival = edgeArrival;

                    if (!bestArrivalTime.containsKey(nextNode) ||
                            newArrival.isBefore(bestArrivalTime.get(nextNode))) {
                        bestArrivalTime.put(nextNode, newArrival);
                        List<STEdge> newRoute = new ArrayList<>(current.route);
                        newRoute.add(edge);
                        queue.offer(new RouteNode(nextNode, newRoute, newArrival));
                    }
                }
            }
        }

        if (bestRoute != null && !bestRoute.isEmpty()) {
            STEdge lastEdge = bestRoute.get(bestRoute.size() - 1);
            if (!lastEdge.getToNode().getAirport().getIcao().equals(destIcao)) {
                return null;
            }
        }

        return bestRoute;
    }

    // Clase auxiliar para BFS con tiempos
    private static class RouteNode {
        STNode node;
        List<STEdge> route;
        Instant arrivalTime;

        RouteNode(STNode node, List<STEdge> route, Instant arrivalTime) {
            this.node = node;
            this.route = route;
            this.arrivalTime = arrivalTime;
        }
    }
    /**
     * Genera población inicial usando heurísticas greedy
     */
    private List<Chromosome> initializePopulation(Baggage baggage, Instant startTime) {
        List<Chromosome> population = new ArrayList<>();

        for (int i = 0; i < POPULATION_SIZE; i++) {
            List<STEdge> route = generateRandomRoute(baggage, startTime);
            if (!route.isEmpty()) {
                population.add(new Chromosome(route));
            }
        }

        // Si no generamos suficientes, llenar con variaciones greedy
        while (population.size() < POPULATION_SIZE) {
            List<STEdge> route = generateGreedyRoute(baggage, startTime);
            if (!route.isEmpty()) {
                population.add(new Chromosome(route));
            } else {
                break; // No hay rutas posibles
            }
        }

        return population;
    }

    /**
     * Genera ruta aleatoria válida
     */
    private List<STEdge> generateRandomRoute(Baggage baggage, Instant startTime) {
        List<STEdge> route = new ArrayList<>();
        STEdge currentEdge = getInitialWaitEdge(baggage, startTime);

        if (currentEdge == null) return route;

        route.add(currentEdge);
        int maxSteps = 20; // Evitar loops infinitos
        int steps = 0;

        while (!isAtDestination(currentEdge, baggage) && steps < maxSteps) {
            List<STEdge> possibleEdges = graph.getEdgesFrom(currentEdge.getToNode());
            if (possibleEdges.isEmpty()) break;

            // Selección aleatoria con sesgo hacia vuelos que se acercan al destino
            currentEdge = selectBiasedEdge(possibleEdges, baggage);
            route.add(currentEdge);
            steps++;
        }

        return isAtDestination(currentEdge, baggage) ? route : new ArrayList<>();
    }

    /**
     * Genera ruta greedy (mejor opción local)
     */
    private List<STEdge> generateGreedyRoute(Baggage baggage, Instant startTime) {
        List<STEdge> route = new ArrayList<>();
        STEdge currentEdge = getInitialWaitEdge(baggage, startTime);

        if (currentEdge == null) return route;

        route.add(currentEdge);
        int maxSteps = 20;
        int steps = 0;

        while (!isAtDestination(currentEdge, baggage) && steps < maxSteps) {
            List<STEdge> possibleEdges = graph.getEdgesFrom(currentEdge.getToNode());
            if (possibleEdges.isEmpty()) break;

            // Elegir edge que minimice tiempo de llegada al destino
            currentEdge = selectGreedyEdge(possibleEdges, baggage);
            route.add(currentEdge);
            steps++;
        }

        return isAtDestination(currentEdge, baggage) ? route : new ArrayList<>();
    }

    /**
     * Función de fitness: 0.0 a 1.0
     * Considera: tiempo de entrega, cumplimiento deadline, uso de capacidad
     */
    private double evaluateFitness(Chromosome chromosome, Baggage baggage) {
        if (chromosome.edges.isEmpty()) return 0.0;

        Instant arrivalTime = calculateArrivalTime(chromosome.edges);
        Instant deadline = baggage.getDeadlineUtc();

        // Calcular horas de viaje
        Instant startTime = chromosome.edges.get(0).getFromNode().getTimeUtc();
        long totalHours = java.time.Duration.between(startTime, arrivalTime).toHours();

        // Determinar máximo permitido según tipo de viaje
        String originIcao = baggage.getShipment().getShipmentData().getOriginAirport().getIcao();
        String destIcao = baggage.getDestIcao();
        boolean isSameContinent = isSameContinent(originIcao, destIcao);
        int maxAllowedHours = isSameContinent ? 24 : 48;

        // Si la ruta excede el tiempo máximo, fitness = 0
        if (totalHours > maxAllowedHours) {
            return 0.0;
        }

        // Factor 1: Cumplimiento de deadline (60%)
        double deadlineScore;
        if (arrivalTime.isBefore(deadline) || arrivalTime.equals(deadline)) {
            deadlineScore = 1.0;
        } else {
            long delayHours = java.time.Duration.between(deadline, arrivalTime).toHours();
            deadlineScore = Math.max(0, 1.0 - (delayHours / 24.0));
        }

        // Factor 2: Tiempo total (30%) - penalizar rutas lentas
        double timeScore = Math.max(0, 1.0 - (totalHours / (double) maxAllowedHours));

        // Factor 3: Uso de capacidad (10%)
        double capacityScore = evaluateCapacityUsage(chromosome.edges);

        return (deadlineScore * 0.6) + (timeScore * 0.3) + (capacityScore * 0.1);
    }

    private boolean isSameContinent(String origin, String dest) {
        // Mapa de continentes por código ICAO
        Map<String, String> continent = new HashMap<>();
        // Europa
        continent.put("EBCI", "EU"); continent.put("EDDI", "EU"); continent.put("EHAM", "EU");
        continent.put("EKCH", "EU"); continent.put("LBSF", "EU"); continent.put("LDZA", "EU");
        continent.put("LKPR", "EU"); continent.put("LOWW", "EU"); continent.put("OPKC", "EU");
        continent.put("OSDI", "EU"); continent.put("OYSN", "EU"); continent.put("UBBB", "EU");
        continent.put("UMMS", "EU"); continent.put("VIDP", "EU");
        // Asia
        continent.put("OMDB", "AS"); continent.put("OAKB", "AS"); continent.put("OJAI", "AS");
        continent.put("OOMS", "AS"); continent.put("OERK", "AS");
        // América
        continent.put("SABE", "AM"); continent.put("SBBR", "AM"); continent.put("SCEL", "AM");
        continent.put("SEQM", "AM"); continent.put("SGAS", "AM"); continent.put("SKBO", "AM");
        continent.put("SLLP", "AM"); continent.put("SPIM", "AM"); continent.put("SUAA", "AM");
        continent.put("SVMI", "AM");

        String contOrigin = continent.get(origin);
        String contDest = continent.get(dest);
        return contOrigin != null && contOrigin.equals(contDest);
    }

    /**
     * Cruce de dos padres (orden preservado)
     */
    private Chromosome crossover(Chromosome parent1, Chromosome parent2) {
        if (parent1.edges.isEmpty() || parent2.edges.isEmpty()) {
            return new Chromosome(new ArrayList<>(parent1.edges));
        }

        int size1 = parent1.edges.size();
        int size2 = parent2.edges.size();

        int point1 = random.nextInt(size1);
        int point2 = random.nextInt(size2);

        List<STEdge> childEdges = new ArrayList<>();

        // Tomar primera parte del padre1 hasta point1
        for (int i = 0; i <= point1 && i < size1; i++) {
            childEdges.add(parent1.edges.get(i));
        }

        // Agregar segmento del padre2 que sea compatible
        STEdge lastEdge = childEdges.get(childEdges.size() - 1);
        boolean found = false;

        for (int i = point2; i < size2; i++) {
            STEdge edge = parent2.edges.get(i);
            if (edge.getFromNode().equals(lastEdge.getToNode())) {
                childEdges.add(edge);
                found = true;
                break;
            }
        }

        // Si no se encontró continuación, completar con greedy
        if (!found && !childEdges.isEmpty()) {
            List<STEdge> remainder = generateGreedyRouteFromNode(
                    childEdges.get(childEdges.size() - 1).getToNode()
            );
            childEdges.addAll(remainder);
        }

        return new Chromosome(childEdges);
    }

    /**
     * Mutación: reemplazar un segmento de la ruta
     */
    private void mutate(Chromosome chromosome) {
        if (chromosome.edges.isEmpty()) return;

        for (int i = 0; i < chromosome.edges.size(); i++) {
            if (random.nextDouble() < MUTATION_RATE) {
                // Intentar reemplazar desde este punto
                STEdge currentEdge = chromosome.edges.get(i);
                List<STEdge> alternative = generateRandomRouteFromNode(currentEdge.getToNode());

                if (!alternative.isEmpty()) {
                    // Reemplazar el resto de la ruta
                    List<STEdge> newEdges = new ArrayList<>(chromosome.edges.subList(0, i + 1));
                    newEdges.addAll(alternative);
                    chromosome.edges = newEdges;
                    break;
                }
            }
        }
    }

    /**
     * Selección por torneo
     */
    private Chromosome tournamentSelect(List<Chromosome> population) {
        Chromosome best = null;
        for (int i = 0; i < TOURNAMENT_SIZE; i++) {
            int idx = random.nextInt(population.size());
            Chromosome contender = population.get(idx);
            if (best == null || contender.fitness > best.fitness) {
                best = contender;
            }
        }
        return best;
    }

    // Métodos auxiliares

    private STEdge getInitialWaitEdge(Baggage baggage, Instant startTime) {
        var entryNode = graph.resolveEntryNode(baggage.getShipment().getShipmentData());
        if (entryNode == null) return null;
        return graph.getWaitEdgeFrom(entryNode);
    }

    private boolean isAtDestination(STEdge currentEdge, Baggage baggage) {
        if (currentEdge == null) return false;
        String destIcao = baggage.getShipment().getShipmentData().getDestAirport().getIcao();
        return currentEdge.getToNode().getAirport().getIcao().equals(destIcao);
    }

    private Instant calculateArrivalTime(List<STEdge> route) {
        if (route.isEmpty()) return Instant.MAX;
        return route.get(route.size() - 1).getToNode().getTimeUtc();
    }

    private double evaluateCapacityUsage(List<STEdge> route) {
        // Simplificado: castigar vuelos con alta ocupación
        double totalUtilization = 0;
        int flightCount = 0;

        for (STEdge edge : route) {
            if (edge instanceof FlightEdge flight) {
                totalUtilization += flight.getLoad() / (double) flight.getCapacity();
                flightCount++;
            }
        }

        return flightCount > 0 ? 1.0 - (totalUtilization / flightCount) : 1.0;
    }

    private STEdge selectBiasedEdge(List<STEdge> edges, Baggage baggage) {
        // Sesgo hacia vuelos que se acercan al destino
        String destIcao = baggage.getShipment().getShipmentData().getDestAirport().getIcao();

        return edges.stream()
                .min((e1, e2) -> {
                    double score1 = calculateDestinationProximity(e1, destIcao);
                    double score2 = calculateDestinationProximity(e2, destIcao);
                    return Double.compare(score2, score1);
                })
                .orElse(edges.get(0));
    }

    private STEdge selectGreedyEdge(List<STEdge> edges, Baggage baggage) {
        String destIcao = baggage.getShipment().getShipmentData().getDestAirport().getIcao();

        return edges.stream()
                .min((e1, e2) -> {
                    double score1 = calculateDestinationProximity(e1, destIcao);
                    double score2 = calculateDestinationProximity(e2, destIcao);
                    return Double.compare(score2, score1);
                })
                .orElse(edges.get(0));
    }

    private double calculateDestinationProximity(STEdge edge, String destIcao) {
        if (edge.getToNode().getAirport().getIcao().equals(destIcao)) {
            return 1.0; // Llegó al destino
        }

        // Heurística simple: prefiero vuelos que me acerquen geográficamente
        // En un sistema real, aquí iría cálculo de distancia geodésica
        return 0.5;
    }

    private List<STEdge> generateRandomRouteFromNode(STNode startNode) {
        List<STEdge> route = new ArrayList<>();
        STEdge currentEdge = graph.getWaitEdgeFrom(startNode);
        if (currentEdge == null) return route;

        route.add(currentEdge);
        int maxSteps = 20;
        int steps = 0;

        while (steps < maxSteps) {
            List<STEdge> possibleEdges = graph.getEdgesFrom(currentEdge.getToNode());
            if (possibleEdges.isEmpty()) break;

            // Filtrar solo vuelos no cancelados
            List<STEdge> validEdges = new ArrayList<>();
            for (STEdge e : possibleEdges) {
                if (e instanceof FlightEdge flight && !flight.isCancelled()) {
                    validEdges.add(e);
                } else if (!(e instanceof FlightEdge)) {
                    validEdges.add(e);
                }
            }

            if (validEdges.isEmpty()) break;

            currentEdge = validEdges.get(random.nextInt(validEdges.size()));
            route.add(currentEdge);
            steps++;
        }

        return route;
    }

    private List<STEdge> generateGreedyRouteFromNode(STNode startNode) {
        List<STEdge> route = new ArrayList<>();
        STEdge currentEdge = graph.getWaitEdgeFrom(startNode);

        if (currentEdge == null) return route;

        route.add(currentEdge);
        int maxSteps = 20;
        int steps = 0;

        while (steps < maxSteps && !route.isEmpty()) {
            List<STEdge> possibleEdges = graph.getEdgesFrom(currentEdge.getToNode());
            if (possibleEdges.isEmpty()) break;

            // Greedy: elegir el que tenga menor tiempo de llegada
            currentEdge = possibleEdges.stream()
                    .min(Comparator.comparing(e -> e.getToNode().getTimeUtc()))
                    .orElse(possibleEdges.get(0));
            route.add(currentEdge);
            steps++;
        }

        return route;
    }

    /**
     * Cromosoma interno (una ruta completa)
     */
    private static class Chromosome {
        List<STEdge> edges;
        double fitness;

        Chromosome(List<STEdge> edges) {
            this.edges = edges;
            this.fitness = 0.0;
        }
    }
}