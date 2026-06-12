# Estado de implementación

---

## HECHO ✓

### domain/model/graph
- [x] `STNode` — nodo (aeropuerto, instante UTC), equals/hashCode por (airport, timeUtc)
- [x] `STEdge` — arista abstracta con fromNode, toNode, costMinutes(), assign/release abstractos
- [x] `FlightEdge` — arista de vuelo, capacidad, load, cancelled flag, ID con fecha
- [x] `WaitEdge` — arista de espera en aeropuerto
- [x] `SpaceTimeGraph` — rolling horizon, expansión, reducción, cancelación, colas de baggages
- [x] `SpaceTimeGraph.getWaitEdgeFrom(STNode)` — primer WaitEdge saliente de un nodo
- [x] `SpaceTimeGraph.addShipment` — asigna `currentEdge` al WaitEdge del nodo de entrada
- [x] `AirportDataDTO` — ICAO, ciudad, país, continente, GMT offset, capacidad, coordenadas
- [x] `FlightScheduleDataDTO` — vuelo recurrente, ID `ORIG-DEST-HH:mm`
- [x] `ShipmentDataDTO` — pedido, calcula deadline desde DeliveryTypeValue
- [x] `DeliveryType`, `DeliveryTypeValue`, `DeliveryTypeValues` — INTRACONTINENTAL 12 h, INTERCONTINENTAL 24 h
- [x] `Baggage` — `currentEdge`, `expectedRoute`, `routeTraveled`, `confirmNextEdge`, `trimExpectedRouteFrom`, `getCurrentAirport()`
- [x] `Shipment` — genera baggages hijos, calcula deadlineUtc
- [x] `GraphProjection` — interface marker; todos los snapshots de optimizador la implementan

### domain/simulator
- [x] `SimulationClock` — reloj escalado, pausa/resume con totalPausedMs, effectiveWallTimeMs
- [x] `SimulationRunner` — loop DelayQueue, switch pattern matching Java 21; `running=true` en `init()`; stats ALNS por iteración
- [x] `SimulationRunner.handleFlightDeparture` — WaitEdge → FlightEdge al embarcar
- [x] `SimulationRunner.handleFlightArrival` — FlightEdge → WaitEdge al llegar; entrega si es destino; re-pende si ruta vacía
- [x] `SimulationRunner.handleRouteSolution` — descarta rutas obsoletas (primer vuelo ya partió); aplica rutas válidas
- [x] `SimulationConfig` — record: SolverTimingMode, OptimizerMode, DataSource, speedFactor, fechas, minutos de conexión
- [x] `StatePublisher` — interface de dominio; extiende `AutoCloseable` (close() no-op por defecto); implementación concreta en `presentation/websocket/`
- [x] Todos los eventos: `HorizonExpandEvent`, `FlightDepartureEvent`, `FlightArrivalEvent`, `FlightCancelledEvent`, `NewShipmentEvent`, `RouteSolutionEvent`, `SimulationEndEvent`
- [x] DTOs internos (`domain/simulator/dto/`): `FlightScheduledDTO`, `FlightDepartedDTO`, `BaggageDepartedDTO`, `FlightArrivedDTO`, `BaggageArrivedDTO`, `BaggageDeliveredDTO`, `BaggagePendingDTO`, `FlightCancelledDTO`, `ShipmentCreatedDTO`, `BaggageAssignedDTO`
- [x] Feeds: `ShipmentFeed`, `CancellationFeed`, `CancellationEntry`

### domain/simulator/thread
- [x] `ShipmentInjectorThread` — consume `ShipmentFeed`, pause-aware
- [x] `CancellationInjectorThread` — consume `CancellationFeed`
- [x] `AlnsThread` — snapshot thread-safe (`new ArrayList<>(pendingBaggages)`), envía `RouteSolutionEvent`; flag `isActive`
- [x] `GeneticThread` — análogo al AlnsThread; flag `isActive`

### domain/optimizer
- [x] `RoutingOptimizer` — interface Strategy: `optimize(GraphProjection)`
- [x] `SolutionResult` — record: routes, isPartial, generatedAt, unroutedCount, alnsScore
- [x] `AlnsProjection` — snapshot inmutable para ALNS
- [x] `ALNSAlgorithm` — ciclo destroy/repair completo con Roulette Wheel, Simulated Annealing, GRASP initializer
- [x] `destroy/` — RandomRemoval, ShawRemoval, WorstRemoval, TimeWindowRemoval, OverloadedFlightRemoval
- [x] `repair/` — GreedyInsertion, RegretInsertion, MinWaitInsertion
- [x] `GeneticProjection` — snapshot inmutable para Genético
- [x] `GeneticAlgorithm` — stub: retorna `SolutionResult.empty()`

### domain/util
- [x] `TimeUtils` — localToUtc, utcToLocal, combineDateAndLocalTime

### infrastructure/files
- [x] `AirportParser` — UTF-16, continente desde encabezado de sección
- [x] `FlightParser` — ISO-8859-1, formato `ORIG-DEST-HH:MM-HH:MM-cap`
- [x] `ShipmentParser` — parseLine + skipHeader
- [x] `CancellationParser` — depTimeUtc desde flightKey + fecha
- [x] `TxtShipmentFeed implements ShipmentFeed` — k-way merge cronológico de todos los `_envios_ICAO_.txt`
- [x] `TxtCancellationFeed implements CancellationFeed` — streaming de `cancelaciones.txt`
- [x] `TxtAvailableDaysService implements AvailableDaysPort` — escanea archivos, extrae fechas únicas (partes[1] de cada línea)

### infrastructure/security
- [x] `JwtService` — genera y valida tokens HS256; secret desde `${JWT_SECRET}` (variable de entorno), expiración desde `application.yml`
- [x] `JwtAuthFilter` — `OncePerRequestFilter`; extrae Bearer token, valida, setea SecurityContext; logging con SLF4J (nivel DEBUG para auth, WARN para fallos)
- [x] `SecurityConfig` — STATELESS, CSRF disabled; security headers HTTP; rutas públicas: `POST /auth/login`, `GET /simulations/*/ws`, `/swagger-ui/**`, `/v3/api-docs/**`
- [x] Swagger UI — `springdoc-openapi-starter-webmvc-ui 2.6.0`; disponible en `http://localhost:8080/swagger-ui.html`; autorización con `Bearer <token>` desde el botón Authorize

### infrastructure/config
- [x] `SpringConfig` — `@Bean` para SimulationRegistry, RunSimulationUseCase, QuerySimulationUseCase, TxtAvailableDaysService; factory `InMemoryStatePublisher::new` por sesión
- [x] `application.yml` — puerto 8080, rutas de archivos de datos, `jwt.secret` desde `${JWT_SECRET}`, `app.cors.allowed-origins` configurable, logging

### application
- [x] `SimulationControlPort` — interface in: `start(StartSimulationCommand) → sessionId`, `pause(id)`, `resume(id)`, `stop(id)`
- [x] `SimulationQueryPort` — interface in: `getSession(id)`, `getDashboard(id)`, `getBaggageState(sessionId, baggageId)`, `getSnapshot(id)`
- [x] `AvailableDaysPort` — interface in: `getAvailableDates() → List<LocalDate>`
- [x] `SimulationRegistry` — doble mapa O(1): `sessions` (sessionId→sesión) + `byUser` (username→sessionId); `findOrThrow`, `findByUser`, `hasActiveSession` (STARTING/RUNNING/PAUSED), `remove` limpia ambos mapas
- [x] `SimulationSession` — guarda `username` del creador + `StatePublisher` por sesión; `interruptAll()` llama `publisher.close()` además de interrumpir hilos
- [x] `StartSimulationCommand` — record: username, dataSource, solverTimingMode, optimizerMode, simStart, simEnd, speedFactor
- [x] `RunSimulationUseCase implements SimulationControlPort` — recibe `Function<String, StatePublisher>` factory; verifica `hasActiveSession` antes de crear (→ 409); crea publisher por sesión; `validateCombination()` lanza `UnsupportedOperationException` para modos no implementados
- [x] `QuerySimulationUseCase implements SimulationQueryPort` — `getSession`, `getSessionByUser` (null si no existe), `getDashboard`, `getBaggageState`, `getSnapshot` (vuelos + baggages del horizonte actual)
- [x] `application/dto/` — `SimSessionView`, `DashboardView`, `BaggageView`, `SnapshotView` (con `FlightSnap` y `BaggageSnap` anidados)

### presentation/rest
- [x] `TasfApplication` — `@SpringBootApplication` main
- [x] `GlobalExceptionHandler` — `IllegalArgumentException` → 404, `IllegalStateException` → 409, `UnsupportedOperationException` → 501, `ResponseStatusException` → su propio status (401, 429…), `Exception` → 500 sin detalles internos (ID de referencia en logs); SLF4J
- [x] `AuthController` — `POST /api/v1/auth/login` (recibe `passwordHash` = sha256 hex; back compara con `bcrypt(sha256)` en BD; rate limiting 5 intentos/IP/minuto con Bucket4j), `POST /api/v1/auth/refresh`
- [x] `HashGenerator` — genera `bcrypt(sha256(password))` para insertar/actualizar en BD; incluye el SQL listo para ejecutar
- [x] `DataController` — `GET /api/v1/data/available-days`, `GET /api/v1/data/airports`, `GET /api/v1/data/routes` (requieren JWT)
- [x] `SimulationController` — `POST /api/v1/simulations` (extrae username del `Principal`; 400 campo faltante, 409 sesión activa, 501 combinación no implementada), `GET /api/v1/simulations/mine` (sesión activa del usuario o 404), `GET /:id`, `POST .../pause`, `POST .../resume`, `POST .../stop`
- [x] `MonitoringController` — `GET /api/v1/simulations/:id/dashboard`, `GET /api/v1/simulations/:id/snapshot`
- [x] `TrackingController` — `GET /api/v1/simulations/:id/baggage/:baggageId`

### presentation/websocket — streaming en tiempo real (sin Redis)
- [x] `InMemoryStatePublisher implements StatePublisher` — `BlockingQueue` + hilo daemon drenador; broadcasta a sesiones WS suscritas; `close()` interrumpe el drenador; `seq` `AtomicLong` incremental por sesión en cada envelope
- [x] `SimulationWebSocketHandler` — registra/desregistra `WebSocketSession` en `InMemoryStatePublisher` al conectar/desconectar; sin hilo propio por conexión
- [x] `JwtHandshakeInterceptor` — valida JWT vía query param `?token=<jwt>` antes de aceptar el handshake WS
- [x] `WebSocketConfig` — registra el handler en `/api/v1/simulations/{id}/ws`; orígenes desde `${app.cors.allowed-origins}` (no más `"*"`)
- [x] `spring-boot-starter-websocket` agregado al `pom.xml`

### pruebas manuales (Postman)
- [x] login, refresh
- [x] POST/GET/pause/resume/stop simulación
- [x] dashboard, snapshot
- [x] WebSocket — suscripción y eventos en vivo; `seq` incremental por sesión implementado
- [ ] disrupciones (`POST /simulations/:id/disruptions`)
- [ ] tracking baggage (`GET /simulations/:id/baggage/:baggageId` y `.../route`)
- [ ] admin merge (airports, flights) y DELETEs (shipments, historical)

### tests
- [x] `BaggageTest` — 12 tests: estado, rutas, transiciones de currentEdge
- [x] `SpaceTimeGraphTest` — 13 tests: expansión, cancelación, assign/unassign, getBaggagesAffectedBy
- [x] `SimulationClockTest` — 7 tests: pausa/resume, escala de tiempo, toWallDeadlineMs
- [x] `RouteFinderTest` — 8 tests: ruta directa, escalas, deadline, capacidad, blacklist
- [x] `BaggageSolutionTest` — 10 tests: score, deepCopy, flightHasCapacity, addRoute/removeRoute

---

## PENDIENTE

### domain/optimizer/genetic
- [ ] `operators/` — crossover, mutación, selección (el `GeneticAlgorithm` actual es un stub)

### Modos de timing del optimizador
- [ ] `PAUSE` — runner pausa el reloj antes de lanzar el optimizador; reanuda al recibir `RouteSolutionEvent`
- [ ] `EVENT_DRIVEN` — sin reloj de pared; avanza evento a evento

### infrastructure/persistence — histórico
- [ ] `PostgreSQLFlightRepository implements HistoricalFlightRepository`
- [ ] `PostgreSQLBaggageRepository implements HistoricalBaggageRepository`
- [ ] Integrar persistencia en `reduceGraph` — guardar antes de podar el rolling horizon
- [ ] Esquema SQL: `flight_completed`, `baggage_delivered`
- [ ] Dependencias: `spring-boot-starter-data-jpa`, driver PostgreSQL

### infrastructure/api — simulador tiempo real
- [ ] `ApiShipmentFeed implements ShipmentFeed` — recibe ShipmentDataDTO vía REST en lugar de archivo
- [ ] `ApiCancellationFeed implements CancellationFeed` — análogo

### application
- [ ] `HistoricalFlightRepository` — interface out (port)
- [ ] `HistoricalBaggageRepository` — interface out (port)
- [ ] `HistoricalQueryPort` — interface in: getCompletedFlights, getDeliveredBaggages
- [ ] `QueryCurrentStateUseCase` — enruta: grafo (>= marginLowerCompleted) o PostgreSQL (< marginLowerCompleted)

### application — ports y datos nuevos para los paneles del front (roadmap Fases 2–8)

- [ ] Añadir a `SimulationQueryPort`: `getFlights(id)`, `getFlightDetail(id, flightId)`, `getAirportsLive(id)`, `getAirportInbound(id, icao)`, `getAirportOutbound(id, icao)`, `getAirportTransit(id, icao)`, `getShipmentsPlanned(id)`, `getShipmentsInFlight(id)`, `getShipmentsDelivered(id, hours)`, `getSimulationReport(id)`
- [ ] Añadir `Map<String, Shipment> activeShipments` en `SimulationSession` — poblado cuando llega `NewShipmentEvent`, necesario para agrupar maletas por envío en los endpoints de shipments y airports
- [ ] Añadir campo `deliveredAt: Instant` en `Baggage` (o en el registro de `deliveredBaggages` del runner) para poder filtrar entregas por ventana de tiempo simulado

### presentation/rest — endpoints nuevos (roadmap Fases 2–8)

Todos bajo `/api/v1/simulations/{id}/`. Ver contratos completos en `APIS.md`.

**Panel de vuelos (Fase 2)**
- [ ] `FlightsController` — `GET /simulations/:id/flights` — lista vuelos del horizonte con `occupancyLevel` (`EMPTY` = 0 %, `GREEN` ≤ 60 %, `AMBER` ≤ 85 %, `RED` > 85 %)
- [ ] `FlightsController` — `GET /simulations/:id/flights/:flightId` — detalle de un vuelo con los envíos y maletas a bordo

**Panel de almacenes (Fase 3)**
- [ ] `AirportLiveController` — `GET /simulations/:id/airports` — aeropuertos con carga en tiempo real y `occupancyLevel`
- [ ] `AirportLiveController` — `GET /simulations/:id/airports/:icao/inbound` — vuelos planificados que llegarán con sus maletas asignadas
- [ ] `AirportLiveController` — `GET /simulations/:id/airports/:icao/outbound` — vuelos planificados que saldrán con sus maletas asignadas
- [ ] `AirportLiveController` — `GET /simulations/:id/airports/:icao/transit` — maletas actualmente esperando conexión en ese aeropuerto (currentEdge = WaitEdge en ese nodo)

**Panel de envíos (Fase 4)**
- [ ] `ShipmentPanelController` — `GET /simulations/:id/shipments/planned` — envíos con ruta asignada esperando vuelo (status WAITING, agrupados por shipmentId)
- [ ] `ShipmentPanelController` — `GET /simulations/:id/shipments/in-flight` — envíos actualmente en el aire (status IN_FLIGHT, agrupados)
- [ ] `ShipmentPanelController` — `GET /simulations/:id/shipments/delivered?hours=N` — entregas de las últimas N horas de tiempo simulado (default 4); requiere `deliveredAt` en Baggage

**Reportes (Fase 8)**
- [ ] `ReportController` — `GET /simulations/:id/reports/summary` — métricas finales: totales, SLA, throughput, rutas más usadas

### WebSocket — validación pendiente (Fase 6, D14)

- [ ] Confirmar que `POST /simulations/:id/disruptions` → `controlPort.injectDisruption()` emite `FLIGHT_CANCELLED` + `BAGGAGE_PENDING` por `StatePublisher` → WebSocket. Trazar el flujo desde `RunSimulationUseCase.injectDisruption` hasta `InMemoryStatePublisher.publish`.

---

## DEUDA TÉCNICA

- [ ] Eliminar `Main.java` placeholder vacío de IntelliJ
- [ ] Eliminar constante `LEAD_MINUTES` de `CancellationInjectorThread` (no aplica)
- [ ] `scheduledFlightEdgeIds` en `SimulationRunner` — será innecesario cuando exista persistencia histórica
- [ ] `deliveredBaggages` en `SimulationRunner` — migrar a `PostgreSQLBaggageRepository`
- [ ] Remover demo de pausa hardcodeada (500ms/1000ms) de `SimulationMain`
- [ ] `classpathPath()` en `SpringConfig` no funciona dentro de un JAR — mover archivos de datos fuera del classpath cuando se despliegue (ver `DEPLOY.md`)
- [ ] Descripción del WS en `SimulationWebSocketHandler` menciona XREAD (heredado de la versión Redis) — actualizar comentario
- [ ] Unificar los dos `GeneticAlgorithm`: `domain.algorithm.GeneticAlgorithm` (implementado, usado por `SpaceTimeGraph.optimizeAndAssignRoutes`) vs `domain.optimizer.genetic.GeneticAlgorithm` (stub, implementa `RoutingOptimizer`). Uno de los dos sobra.
- [ ] `SimpleRoutePlanner` importado en `SpaceTimeGraph` pero nunca usado — eliminar import o integrar
- [ ] `HashGenerator.java` en código de producción (`infrastructure/security`) — mover a `src/test/` o eliminar tras crear el usuario
- [ ] Validación Caso 3 de `cancelFlight` siempre falsa — compara `Instant` con `FlightScheduleDataDTO` (tipos incompatibles)
