// SpaceTimeGraph.java
/* Grafo que contiene un estado total de la simulación
* Contiene:
* - Un grafo del estado actual con un horizonte definido (y el real)
*
* */
package com.tasf.b2b.domain.model.graph;

import com.tasf.b2b.domain.algorithm.GeneticAlgorithm;
import com.tasf.b2b.domain.algorithm.SimpleRoutePlanner;
import com.tasf.b2b.domain.model.graph.componentsgraph.FlightEdge;
import com.tasf.b2b.domain.model.graph.componentsgraph.STEdge;
import com.tasf.b2b.domain.model.graph.componentsgraph.STNode;
import com.tasf.b2b.domain.model.graph.componentsgraph.WaitEdge;
import com.tasf.b2b.domain.model.graph.immovable.AirportDataDTO;
import com.tasf.b2b.domain.model.graph.immovable.DeliveryTypeValues;
import com.tasf.b2b.domain.model.graph.immovable.FlightScheduleDataDTO;
import com.tasf.b2b.domain.model.graph.immovable.ShipmentDataDTO;
import com.tasf.b2b.domain.model.graph.movable.Baggage;
import com.tasf.b2b.domain.model.graph.movable.Shipment;
import com.tasf.b2b.domain.util.TimeUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class SpaceTimeGraph {
    // --Ventana de expansión--------
    // Por defecto 4 días: cubre el DeliveryType más largo (INTERCONTINENTAL)
    private static final int DEFAULT_HORIZON_DAYS = 4;
    private final int horizonDays;
    private Instant horizonCompleted; //El horizonte hasta donde ya se ha expandido (es un inicio de un día)

    //--Margen de eliminación del grafo---------
    private static final int DEFAULT_MARGIN_LOWER_DAYS = 4;
    private final int marginLowerDays;
    private Instant marginLowerCompleted; //El horizonte hasta donde ya se ha eliminado


    // --Estructura principal: timelinesNodes y adjEdges---------
    // ICAO → mapa ordenado por tiempo UTC → nodo ST (solo las que entran en el caso)
    // TreeMap permite encontrar nodo previo/siguiente en O(log n) con lower/higher
    // Es solo el estado actual, no el histórico
    private final Map<String, TreeMap<Instant, STNode>> timelinesNodes;

    // Lista global de adyacencia: nodo → aristas salientes (solo las que entran en el caso)
    private final Map<STNode, List<STEdge>> adjEdges;

    // Índice de vuelos por ID de FlightScheduleData para cancelación
    // Un FlightScheduleData puede generar múltiples FlightEdges (uno por día expandido)
    // Es solo es del estado actual, no el histórico
    private final Map<String, TreeMap<Instant, FlightEdge>> flightIndex;

    //--Lista de cancelaciones pedientes que suceden después del horizonCompleted
    private final TreeMap<Instant, String> pendingCancellationsFlightsSchedule;

    //--Baggages----------------------------------------
    // Todos los baggages del problema — fuente de verdad para recuperación
    private final List<Baggage> allBaggages;

    // Baggages sin ruta asignada, ordenados por deadline (más urgente primero)
    private final PriorityQueue<Baggage> pendingBaggages;

    // Baggages con ruta asignada, ordenados por deadline
    private final PriorityQueue<Baggage> assignedBaggages;

    //--Aeropuertos, horarios de vuelo y tipos de vuelo (todos fijos) ----------------
    // Acceso rápido a aeropuertos registrados
    private final Map<String, AirportDataDTO> airports;

    // Vuelos diarios registrados — se guardan para expandAllFlights sin pasarlos cada vez
    private final Map<String, FlightScheduleDataDTO> flightsSchedule;

    private final DeliveryTypeValues deliveryTypeValues;

    //Constructor (sin cambio de límite de tiempos)
    public SpaceTimeGraph(int horizonDays, int marginLowerDays) {
        this.horizonDays = horizonDays;
        this.horizonCompleted = Instant.MIN;
        this.marginLowerDays = marginLowerDays;
        this.marginLowerCompleted = Instant.MIN;
        this.timelinesNodes = new HashMap<>();
        this.airports = new LinkedHashMap<>();  // orden de inserción para reproducibilidad
        this.flightIndex = new HashMap<>();
        this.pendingCancellationsFlightsSchedule = new TreeMap<>();
        this.adjEdges = new HashMap<>();
        this.flightsSchedule = new HashMap<>();
        this.deliveryTypeValues = new DeliveryTypeValues();
        this.allBaggages = new ArrayList<>();
        this.pendingBaggages = new PriorityQueue<>(Comparator.comparing(Baggage::getDeadlineUtc));
        this.assignedBaggages = new PriorityQueue<>(Comparator.comparing(Baggage::getDeadlineUtc));
    }

    //Constructor con horizonte límite
    public SpaceTimeGraph() {
        this(DEFAULT_HORIZON_DAYS, DEFAULT_MARGIN_LOWER_DAYS);
    }

    //--Registro inicial antes de la primera expansión------
    //--Registro de un aeropuerto---------------------
    public void addAirport(AirportDataDTO airport) {
        this.airports.put(airport.getIcao(), airport); //Datos fijos de los airports
        this.timelinesNodes.put(airport.getIcao(), new TreeMap<>()); //Punto de un airport donde luego se definirán los timelines
    }

    //TODO: hacer un registro de una lista de airports

    //--Registro de la planificación de vuelos----------------
    // Guarda el horario para usarlo en expandAllFlights sin pasarlo cada vez
    public void addScheduledFlight(FlightScheduleDataDTO flightScheduleData) {
        this.flightsSchedule.put(flightScheduleData.getId(), flightScheduleData); //Datos fijos de los flight diarios
    }

    //TODO: hacer un registro de una lista de programaciones de vuelo

    //--Expansión y reducción del grafo diario -----------
    // Expande todos los vuelos diarios sobre la ventana [initialExpand, horizonCompleted).
    // Itera día a día y por cada día registra todos los vuelos de una vez. Además, luego de expandir elimina los
    // nodos y aristas de todos las listas actualizadas que las contengan. Al final, cancela los vuelos pendientes
    // referentes a esa última expansión y reducción.
    // Debe llamarse después de registrar todos los aeropuertos con addAirport() [Para inicializar]
    // Debe llamarse al inicio de un día de operaciones
    public List<FlightEdge> expandAllFlights(Instant initialExpand) {
        Instant initialExpandTruncated = initialExpand.truncatedTo(ChronoUnit.DAYS); //Se trunca al inicio del día
        //dayCursor es de donde realmente se empezará a expandir
        Instant dayCursor = this.horizonCompleted; //Ya está truncado al inicio
        //Se debería expandir de donde se dejó, pero puede que la nueva fecha supere esto y sea
        //innecesario expandir desde ahí, por eso se expande desde initialExpandTruncated
        if (dayCursor.isBefore(initialExpandTruncated)) dayCursor = initialExpandTruncated;
        Instant horizonEndActual = initialExpandTruncated.plus(this.horizonDays, ChronoUnit.DAYS);

        // Lista de aristas nuevas — devuelta al runner para que programe sus eventos de salida/llegada
        List<FlightEdge> newEdges = new ArrayList<>();

        //Expansión del grafo
        while (dayCursor.isBefore(horizonEndActual)) {
            for (FlightScheduleDataDTO flightScheduleData : this.flightsSchedule.values()) {
                AirportDataDTO orig = this.airports.get(flightScheduleData.getOriginAirport().getIcao());
                AirportDataDTO dest = this.airports.get(flightScheduleData.getDestAirport().getIcao());

                if (orig == null || dest == null)
                    throw new IllegalArgumentException(
                            "Aeropuerto no registrado: " + flightScheduleData.getOriginAirport().getIcao()
                                    + " o " + flightScheduleData.getDestAirport().getIcao());

                //Los resultados saldrán en el mismo día del utc-0
                Instant depUtc = TimeUtils.combineDateAndLocalTime(
                        dayCursor, flightScheduleData.getDepartureTimeLocal(), orig.getGmtOffset());
                Instant arrUtc = TimeUtils.combineDateAndLocalTime(
                        dayCursor, flightScheduleData.getArrivalTimeLocal(), dest.getGmtOffset());

                // Vuelo nocturno: llegada cae al día siguiente
                if (arrUtc.isBefore(depUtc)) {
                    arrUtc = arrUtc.plus(1, ChronoUnit.DAYS);
                }

                // No descarta vuelos, pero todos los vuelos deben salir como máximo
                // el día en que se está evaluando (debería funcionar con combineDateAndLocalTime).
                // Y en general, como máximo en horizonCompleted

                STNode fromNode = this.getOrCreateNode(orig, depUtc);
                STNode toNode = this.getOrCreateNode(dest, arrUtc);

                FlightEdge edge = new FlightEdge(flightScheduleData, fromNode, toNode);

                //Se agrega el flight real a adj (adyacencia) a fromNode y el Flight real al index
                this.adjEdges.get(fromNode).add(edge);
                this.flightIndex.computeIfAbsent(flightScheduleData.getId(), k -> new TreeMap<>()).put(edge.getFromNode().getTimeUtc(),edge);
                //Se registra como nueva arista para notificar al runner
                newEdges.add(edge);
            }

            //Se actualiza el dayCursor
            dayCursor = dayCursor.plus(1, ChronoUnit.DAYS);
        }

        //Actualiza horizonCompleted para que la próxima llamada continúe desde aquí
        this.horizonCompleted = horizonEndActual;


        //Reducción del grafo diario y actualización den marginLowerCompleted
        reduceGraph(initialExpandTruncated);


        //Eliminación de vuelos
        //A este punto ya se expandió y redujo (y se actualizaron los límites)
        //Eliminar las cancelaciones pendientes que ya no se procesarán porque están antes del nuevo marginLowerCompleted
        this.pendingCancellationsFlightsSchedule.headMap(this.marginLowerCompleted).clear();

        //Sacar todos las cancelaciones pendientes que sí se procesarán
        for (Map.Entry<Instant, String> flightCancelled : pendingCancellationsFlightsSchedule.headMap(this.horizonCompleted).entrySet()) {
            cancelFlight(flightCancelled.getValue(), flightCancelled.getKey());
        }

        return newEdges;
    }

    //--Creación de nodos------------
    // Obtiene o crea un STNode para (aeropuerto, instante).
    // Al insertar un nodo nuevo, reconstruye los WaitEdges del tramo afectado:
    //   prev → nuevo → next  (reemplaza el WaitEdge prev → next que existía antes)
    private STNode getOrCreateNode(AirportDataDTO airport, Instant timeUtc) {
        TreeMap<Instant, STNode> timeline = this.timelinesNodes.get(airport.getIcao());

        // Si ya existe, devolverlo directamente
        if (timeline.containsKey(timeUtc)) {
            return timeline.get(timeUtc);
        }

        // Crear nodo nuevo y sus adyacencias
        STNode node = new STNode(airport, timeUtc);
        timeline.put(timeUtc, node);
        this.adjEdges.put(node, new ArrayList<>());

        // Vecinos temporales inmediatos en la línea de tiempo del aeropuerto
        Map.Entry<Instant, STNode> prevEntry = timeline.lowerEntry(timeUtc);
        Map.Entry<Instant, STNode> nextEntry = timeline.higherEntry(timeUtc);

        STNode prevNode = (prevEntry != null) ? prevEntry.getValue() : null;
        STNode nextNode = (nextEntry != null) ? nextEntry.getValue() : null;

        // Si existía un WaitEdge prev → next, eliminarlo (ya no es contiguo)
        if (prevNode != null && nextNode != null) {
            this.adjEdges.get(prevNode).removeIf(
                    e -> e instanceof WaitEdge && e.getToNode().equals(nextNode));
        }

        // WaitEdge prev → nuevo (si hay nodo anterior)
        if (prevNode != null) {
            this.adjEdges.get(prevNode).add(new WaitEdge(airport, prevNode, node));
        }

        // WaitEdge nuevo → next (si hay nodo siguiente)
        if (nextNode != null) {
            this.adjEdges.get(node).add(new WaitEdge(airport, node, nextNode));
        }

        return node;
    }

    //--Reducción del grafo ----
    public void reduceGraph(Instant initialExpandTruncated) {
        //Calcular el nuevo límite inferior (truncado a días para consistencia)
        Instant newMarginLimit = initialExpandTruncated.minus(this.marginLowerDays, ChronoUnit.DAYS);

        // Evitar procesar si no hemos avanzado en el tiempo
        if (!newMarginLimit.isAfter(this.marginLowerCompleted)) {
            return;
        }

        //Sección de limpieza

        //Limpiar Timelines y Nodos (timelinesNodes y adjEdges)
        //Se recorren todos los aeropuertos
        for (String icao : this.timelinesNodes.keySet()) {
            //Se saca su lista de tiempos
            TreeMap<Instant, STNode> timeline = this.timelinesNodes.get(icao);

            // Obtenemos la vista de los nodos que están "caducados" (estrictamente menor)
            SortedMap<Instant, STNode> expiredNodes = timeline.headMap(newMarginLimit);

            if (!expiredNodes.isEmpty()) {
                //Se itera sobre los nodos a eliminar
                for (STNode node : expiredNodes.values()) {
                    // TODO: Aquí se podría persistir el nodo en una BD Histórica antes de borrarlo

                    // Limpiar adjEdgef: Eliminar sus aristas salientes del mapa global de adyacencia
                    List<STEdge> outEdges = this.adjEdges.remove(node); //Se toman las que se eliminarán

                    //Limpiar flightIndex: si había nodos saliendo
                    if (outEdges != null) {
                        //Para todos lo Edges que salgan
                        for (STEdge outEdge : outEdges) {
                            //Solo para los FlighEdges que salgan
                            if (outEdge instanceof FlightEdge outFlight) {
                                String fId = outFlight.getFlightScheduleData().getId();
                                TreeMap<Instant, FlightEdge> fTimeline = this.flightIndex.get(fId);

                                //Solo si el nodo tiene vuelos salientes (puede que no tenga por que se expandió más
                                // allá del límite horizonCompleted)
                                if (fTimeline != null) {
                                    // TODO: Aquí podrías persistir los FlightEdges antes de borrar

                                    // Usamos el tiempo del nodo como clave en el índice de vuelos
                                    fTimeline.remove(node.getTimeUtc());
                                }
                            }
                        }
                    }
                }

                // Limpiar el timeline del aeropuerto (elimina todos los de la vista de un golpe)
                expiredNodes.clear();
            }
        }

        // Actualizar el estado del horizonte inferior
        this.marginLowerCompleted = newMarginLimit;
    }

    //---Cancelación de vuelos----
    // Marca el FlightEdge como cancelado y lo retira de la adyacencia a partir del key de schedule
    // El STNode queda en el grafo — puede ser útil como nodo de espera
    // Se devuelve true si se puede cancelar luego o si ya se canceló ahora
    // Devuelve false si no puede cancelar (cancelación sobre el pasado o sobre uno ya cancelado o en cola)
    public boolean cancelFlight(String flightScheduleKey, Instant depTimeUtc) {
        TreeMap<Instant, FlightEdge> innerMap = this.flightIndex.get(flightScheduleKey);
        if (innerMap != null) {
            //Caso 2: No se puede cancelar un vuelo que ya pasó
            if(depTimeUtc.isBefore(this.marginLowerCompleted)) return false;

            //Caso 3: Si el supuesto vuelo no cumple con el cronograma
            // TODO: esta condición siempre es falsa — compara Instant con FlightScheduleDataDTO (tipos distintos).
            // Cuando se implemente validación manual de cancelaciones, reemplazar con la lógica correcta.
            if(depTimeUtc.equals(this.flightsSchedule.get(flightScheduleKey))) return false;

            //Caso 4: Si la cancelación es sobre un vuelo que no está en el grafo aún (posterior)
            if(depTimeUtc.compareTo(this.horizonCompleted)>= 0){
                //Se guarda para después
                this.pendingCancellationsFlightsSchedule.put(depTimeUtc, flightScheduleKey);
                //Al final de la función expansión se cancela lo que va apareciendo
                return true;
            }

            //Se busca el vuelo particular de ese día
            FlightEdge edgeToRemove = innerMap.get(depTimeUtc);

            if(edgeToRemove != null){
                //Se busca el nodo del que parte
                STNode fromNode = edgeToRemove.getFromNode();
                //Se busca los vuelos que salen de este
                List<STEdge> edges = this.adjEdges.get(fromNode);

                if (edges != null) {
                    //Caso 6: El vuelo sí se puede cancelar
                    edgeToRemove.setCancelled(true);
                    //Se remueve de adjEdges
                    edges.remove(edgeToRemove);
                    //Se remueve de flighIndex
                    innerMap.remove(depTimeUtc);
                    return true;
                }
                else{
                    //Caso 5: El vuelo ya se canceló anteriormente (ya no se encuentra en adjEdges)
                    //ERROR: pero sí se encuentra en flightIndex, no debería
                    throw new IllegalArgumentException(
                            "Vuelo mal eliminado de flightIndex, sí eliminado de adjEdged: " +
                                    edgeToRemove.getIdFlightEdge());

                }
            }
            else{
                //Caso 5: El vuelo ya se canceló anteriormente (ya no se encuentra en flightIndex)
                return false;
            }
        }else {
            //Caso 1: La programación del vuelo ni existe
            return false;
        }

    }

    //TODO: posibilidad de crear una función para insertar vuelo

    //--Registro de shipments----
    // Convierte todos los baggages del shipment en pendientes.
    // Asigna currentEdge al primer WaitEdge disponible desde el nodo de entrada.
    // Si no hay WaitEdge aún (nodo sin sucesor), currentEdge queda null hasta
    // que el optimizador asigne una ruta y el runner lo actualice.
    public void addShipment(Shipment shipment) {
        STNode entryNode = this.resolveEntryNode(shipment.getShipmentData());
        STEdge entryWaitEdge = (entryNode != null) ? this.getWaitEdgeFrom(entryNode) : null;
        for (Baggage baggage : shipment.getBaggages()) {
            baggage.setCurrentEdge(entryWaitEdge);
            if (entryWaitEdge != null) entryWaitEdge.assign(); // WaitEdge.dynamicLoad++
            this.allBaggages.add(baggage);
            this.pendingBaggages.add(baggage);
        }
    }

    // ── Gestión de baggages ───────────────────────────────────────────────────
    // Mueve un baggage de pending a assigned. expectedRoute ya debe estar seteado.
    // Registra la ocupación planificada en cada FlightEdge de la ruta.
    public void assignBaggage(Baggage baggage) {
        for (STEdge e : baggage.getExpectedRoute()) {
            if (e instanceof FlightEdge fe) fe.assign(); // FlightEdge.load++
        }
        this.pendingBaggages.remove(baggage);
        this.assignedBaggages.add(baggage);
    }

    // Mueve un baggage de assigned a pending. Libera la ocupación planificada
    // de cada FlightEdge antes de limpiar la ruta. currentEdge no cambia.
    public void unassignBaggage(Baggage baggage) {
        for (STEdge e : baggage.getExpectedRoute()) {
            if (e instanceof FlightEdge fe) fe.release(); // FlightEdge.load--
        }
        this.assignedBaggages.remove(baggage);
        baggage.clearExpectedRoute();
        this.pendingBaggages.add(baggage);
    }

    // Destruye la ruta esperada de un baggage desde un índice hacia adelante.
    // Libera la ocupación de los FlightEdges eliminados antes de recortar.
    public void unassignBaggageFrom(Baggage baggage, int routeIndex) {
        List<STEdge> route = baggage.getExpectedRoute();
        for (int i = routeIndex; i < route.size(); i++) {
            if (route.get(i) instanceof FlightEdge fe) fe.release(); // FlightEdge.load--
        }
        this.assignedBaggages.remove(baggage);
        baggage.trimExpectedRouteFrom(routeIndex);
        this.pendingBaggages.add(baggage);
    }

    // ── Primer WaitEdge saliente de un nodo ──────────────────────────────────
    // Usado por addShipment y por SimulationRunner tras cada FlightArrivalEvent
    // para actualizar currentEdge del baggage al WaitEdge de la conexión.
    public STEdge getWaitEdgeFrom(STNode node) {
        List<STEdge> edges = this.adjEdges.get(node);
        if (edges == null) return null;
        for (STEdge e : edges) {
            if (e instanceof WaitEdge) return e;
        }
        return null;
    }

    // ── Resolución de nodo de entrada ─────────────────────────────────────────
    // Primer STNode disponible en el aeropuerto origen >= entryDateTimeUtc
    public STNode resolveEntryNode(ShipmentDataDTO shipmentData) {
        String icao = shipmentData.getOriginAirport().getIcao();
        Instant entryUtc = shipmentData.getEntryDateTimeUtc();

        TreeMap<Instant, STNode> timeline = this.timelinesNodes.get(icao);
        if (timeline == null) return null;

        // ceiling: primer nodo con tiempo >= entryUtc
        Map.Entry<Instant, STNode> entry = timeline.ceilingEntry(entryUtc);
        return (entry != null) ? entry.getValue() : null;
    }

    // ── Consultas para operadores ALNS ────────────────────────────────────────

    // Aristas salientes de un nodo (para BFS/DFS en repair)
    public List<STEdge> getEdgesFrom(STNode node) {
        return this.adjEdges.getOrDefault(node, Collections.emptyList());
    }

    // Todos los nodos del grafo (para operadores de destrucción global)
    public Collection<STNode> getAllNodes() {
        List<STNode> all = new ArrayList<>();
        for (TreeMap<Instant, STNode> tl : this.timelinesNodes.values()) {
            all.addAll(tl.values());
        }
        return all;
    }

    // Aeropuerto por ICAO
    public AirportDataDTO getAirport(String icao) {
        return this.airports.get(icao);
    }

    // Todos los aeropuertos registrados (datos fijos: capacidad, ubicación, continente).
    // Usado por el snapshot para reportar ocupación de almacenes coordinada con el backend.
    public Collection<AirportDataDTO> getAllAirports() {
        return this.airports.values();
    }

    // Nodo exacto por aeropuerto e instante (null si no existe)
    public STNode getNode(String icao, Instant timeUtc) {
        TreeMap<Instant, STNode> timeline = this.timelinesNodes.get(icao);
        if (timeline == null) return null;
        return timeline.get(timeUtc);
    }


    /**
     * Retorna todos los baggages cuya ruta esperada incluye un vuelo cancelado.
     * Llamado por SimulationState al procesar un FlightCancelledEvent, para
     * que el runner los pase al ALNS y les busque ruta alternativa.
     *
     * @param flightScheduleKey ID del vuelo cancelado (ej: "SKBO-SEQM-19:00")
     * @return Lista de baggages afectados (puede estar vacía)
     */
    public List<Baggage> getBaggagesAffectedBy(String flightScheduleKey, Instant depTimeUtc) {
        List<Baggage> affected = new ArrayList<>();
        for (Baggage b : this.allBaggages) {
            boolean uses = b.getExpectedRoute().stream()
                    .anyMatch(e -> e instanceof FlightEdge fe
                            && fe.getFlightScheduleData().getId().equals(flightScheduleKey)
                            && fe.getFromNode().getTimeUtc().equals(depTimeUtc));
            if (uses) affected.add(b);
        }
        return affected;
    }

    public List<FlightScheduleDataDTO> getFlightsSchedule()   { return List.copyOf(this.flightsSchedule.values()); }
    
    /**
     * Devuelve todas las aristas de vuelo del grafo.
     * Usada por ALNS para acceder a los vuelos durante optimización.
     */
    public Collection<FlightEdge> getAllFlightEdges() {
        List<FlightEdge> result = new ArrayList<>();
        for (TreeMap<Instant, FlightEdge> timeMap : this.flightIndex.values()) {
            result.addAll(timeMap.values());
        }
        return result;
    }

    public void optimizeAndAssignRoutes() {
        GeneticAlgorithm ga = new GeneticAlgorithm(this);

        List<Baggage> toAssign = new ArrayList<>(this.pendingBaggages);
        int assigned = 0;
        int failed = 0;

        System.out.println("[OPTIMIZER] Optimizando " + toAssign.size() + " maletas...");

        for (Baggage baggage : toAssign) {
            Instant currentTime = this.horizonCompleted;
            List<STEdge> route = ga.planRoute(baggage, currentTime);

            if (route != null && !route.isEmpty()) {
                // Verificar que la ruta realmente llega al destino
                STEdge lastEdge = route.get(route.size() - 1);
                if (lastEdge.getToNode().getAirport().getIcao().equals(baggage.getDestIcao())) {
                    baggage.setExpectedRoute(route);
                    baggage.setCurrentEdge(route.get(0));
                    assignBaggage(baggage);
                    assigned++;
                } else {
                    System.out.println("[WARN] Ruta no llega a destino: " + baggage.getId() +
                            " -> " + lastEdge.getToNode().getAirport().getIcao());
                    failed++;
                }
            } else {
                failed++;
            }
        }

        System.out.println("[OPTIMIZER] Rutas asignadas: " + assigned +
                ", fallidas: " + failed +
                ", total: " + toAssign.size());
    }

    //--Getters de ventanas-------
    public int getHorizonDays() {
        return horizonDays;
    }

    public Instant getHorizonCompleted() { return this.horizonCompleted; }

    public int getMarginLowerDays() {
        return marginLowerDays;
    }

    public Instant getMarginLowerCompleted() {
        return marginLowerCompleted;
    }



    public List<Baggage> getAllBaggages() {
        return Collections.unmodifiableList(this.allBaggages);
    }
    public PriorityQueue<Baggage> getPendingBaggages()  { return this.pendingBaggages; }
    public PriorityQueue<Baggage> getAssignedBaggages() { return this.assignedBaggages; }
}