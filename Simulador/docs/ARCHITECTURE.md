# Arquitectura del Sistema — TASF Cargo Routing Simulator

## Capas (Hexagonal / Clean Architecture)

```
com.tasf.b2b/
├── domain/          ← modelos, reglas de negocio, algoritmos, simulación. Sin I/O ni frameworks.
├── application/     ← casos de uso, puertos de entrada/salida (interfaces puras).
├── infrastructure/  ← implementaciones concretas: archivos, PostgreSQL, Redis, Spring config.
└── presentation/    ← controladores REST, WebSocket, DTOs de API.
```

**Regla de dependencias — estricta:**
```
presentation   →  application/port/in   (invoca casos de uso)
infrastructure →  application/port/out  (implementa repositorios y publishers)
application    →  domain                (orquesta entidades y servicios de dominio)
domain         →  nada externo          (interfaces propias para lo que necesita)
```

---

## `domain`

Todo lo que es lógica pura: no hay `File`, no hay `Jedis`, no hay `@Component`.  
Si una clase del domain necesita algo externo (publicar estado, persistir), define una interface aquí; infrastructure la implementa.

---

### `domain/model/graph/`

**`SpaceTimeGraph`** — el grafo principal. Estructura de datos mutable y central de toda la simulación. Es la fuente de verdad del estado actual: posición de baggages, vuelos disponibles y cargas. Solo lo escribe el hilo `simulation-runner`. Implementa la ventana deslizante (rolling horizon): `expandAllFlights(Instant)` expande hacia adelante, poda hacia atrás, y devuelve la lista de `FlightEdge` recién añadidos para que el runner programe sus eventos. Expone API para ALNS: `getEdgesFrom`, `getPendingBaggages`, `assignBaggage` (registra `FlightEdge.load`), `unassignBaggage` (libera `FlightEdge.load`), `cancelFlight`, `getWaitEdgeFrom(STNode)`, etc. Al desasignar un baggage, `currentEdge` no cambia — sigue en la misma arista, solo pierde su ruta planificada.

---

### `domain/model/graph/componentsgraph/`

Nodos y aristas del grafo espacio-temporal.

| Clase | Descripción |
|---|---|
| `STNode` | Punto `(aeropuerto, instante UTC)`. Equals/hashCode por `(icao, timeUtc)`. |
| `STEdge` | Arista abstracta. Tiene `fromNode` y `toNode`. |
| `FlightEdge` | Arista de vuelo: capacidad, `load` (ocupación planificada — mantenido por `assignBaggage`/`unassignBaggage`), flag `cancelled`, ID con fecha (`SKBO-SEQM-19:00-20260102`). |
| `WaitEdge` | Arista de espera en el mismo aeropuerto entre dos eventos consecutivos. `dynamicLoad` cuenta baggages actualmente esperando (mantenido por el runner en salida/llegada). Se reconstruye automáticamente al insertar un nodo nuevo entre otros dos. |

---

### `domain/model/graph/immovable/`

Datos de entrada fijos — se cargan una vez al inicio y nunca se mutan.

| Clase | Descripción |
|---|---|
| `AirportDataDTO` | ICAO, ciudad, país, continente, GMT offset (horas enteras), capacidad, lat/lon. |
| `FlightScheduleDataDTO` | Vuelo diario recurrente: origen/destino, hora local salida/llegada, capacidad. ID: `ORIG-DEST-HH:mm`. |
| `ShipmentDataDTO` | Pedido: origen, destino, `entryDateTimeUtc`, cantidad de baggages, cliente. Calcula deadline desde `DeliveryTypeValues`. |
| `DeliveryType` | Enum: `INTRACONTINENTAL` / `INTERCONTINENTAL`. |
| `DeliveryTypeValue` | Duración máxima por tipo: 24 h / 72 h. |
| `DeliveryTypeValues` | Mapa de tipo → valor; determina el tipo comparando continentes de origen y destino. |

---

### `domain/model/graph/movable/`

Entidades activas que se mueven por el grafo durante la simulación.

| Clase | Descripción |
|---|---|
| `Shipment` | Instancia viva de un pedido. Genera sus `Baggage` hijos al construirse. Calcula `deadlineUtc`. |
| `Baggage` | Unidad de carga. Siempre vive sobre una arista del grafo. `currentEdge: STEdge` — la arista que está atravesando ahora: `WaitEdge` si espera en un aeropuerto, `FlightEdge` si está volando. `expectedRoute: ArrayDeque<STEdge>` — FlightEdges planeados (salida ALNS). `routeTraveled: List<STEdge>` — FlightEdges ya confirmados. `isUnassigned()` derivado de `expectedRoute.isEmpty()` — fuente de verdad única para estado de asignación. `getCurrentAirport()` derivado de `currentEdge.getFromNode()`. |

---

### `domain/model/graph/projection/`

Interface base que todos los snapshots del grafo implementan. Solo define el contrato mínimo compartido. Los snapshots concretos viven junto a su optimizador.

| Clase | Descripción |
|---|---|
| `GraphProjection` | Interface base. Permite que el hilo optimizador reciba un snapshot sin depender del tipo concreto. |

---

### `domain/simulator/`

Núcleo de la simulación: reloj, runner, configuración, y las interfaces que el simulador necesita del exterior.

| Clase | Descripción |
|---|---|
| `SimulationRunner` | Loop principal sobre `DelayQueue<SimEvent>`. Único hilo que escribe `SpaceTimeGraph`. Despacha eventos por `switch` pattern matching. Recibe `StatePublisher` por constructor. `running` se activa en `init()` (no en `run()`) para que los hilos optimizadores no salgan al arrancar antes de que el runner empiece. Acumula stats de soluciones ALNS (`solutionCount`, `totalProposed`, `totalStale`, `totalApplied`) y las expone con getters para el resumen final. |
| `SimulationClock` | Reloj escalado con factor de velocidad. Soporta `pause()` / `resume()` sin perder el tiempo transcurrido (`totalPausedMs`). Expone `now()`, `effectiveWallTimeMs()`, `toWallDeadlineMs(Instant)`. |
| `SimulationConfig` | Record inmutable con todos los parámetros de una corrida: `SolverTimingMode`, `OptimizerMode`, `DataSource`, `speedFactor`, `simStart`, `simEnd`, `minConnectionMinutes`, `pickupMinutes`. |
| `StatePublisher` | **Interface de dominio** — define lo que el simulador necesita publicar hacia el exterior. `infrastructure/redis/RedisStatePublisher` la implementa. Sigue el patrón de "puerto requerido": el dominio define la interfaz, la infraestructura provee la implementación. |

```java
// SimulationConfig — enums
enum SolverTimingMode { REAL_TIME, PAUSE, EVENT_DRIVEN }
enum OptimizerMode    { ALNS_ONLY, GENETIC_ONLY, ALNS_ACTIVE_GENETIC_EVAL, GENETIC_ACTIVE_ALNS_EVAL }
enum DataSource       { TXT, MANUAL }
```

---

### `domain/simulator/event/`

Eventos que circulan por la `DelayQueue` del `SimulationRunner`. Todos implementan `SimEvent` (que implementa `Delayed`). El tiempo de disparo real es calculado desde el tiempo de simulación y el `SimulationClock`.

| Evento | Disparado por | Qué hace el runner |
|---|---|---|
| `HorizonExpandEvent` | Runner (auto-agenda el día siguiente) | Expande el grafo; programa `FlightDeparture` y `FlightArrival` de los nuevos vuelos. |
| `FlightDepartureEvent` | Runner (al expandir horizonte) | Para cada baggage cuyo `peekNextEdge() == fe`: libera `WaitEdge` anterior (`release()`), llama `baggage.setCurrentEdge(fe)` (`WaitEdge → FlightEdge`). Publica `FLIGHT_DEPARTED` + `BAGGAGE_DEPARTED` por cada baggage a bordo. |
| `FlightArrivalEvent` | Runner (al expandir horizonte) | Para cada baggage cuyo `getCurrentEdge() == fe`: llama `confirmNextEdge()` + `setCurrentEdge(nextWaitEdge)` + `waitEdge.assign()` (`FlightEdge → WaitEdge`). Si llegó a destino: entregado. Si ruta vacía: vuelve a pending. Publica `FLIGHT_ARRIVED` + `BAGGAGE_ARRIVED` / `BAGGAGE_DELIVERED`. |
| `FlightCancelledEvent` | `CancellationInjectorThread` | Cancela el vuelo en el grafo; desasigna baggages afectados (los que tienen ese vuelo en `expectedRoute`); publica `FLIGHT_CANCELLED` + `BAGGAGE_PENDING`. |
| `NewShipmentEvent` | `ShipmentInjectorThread` | Crea `Shipment`, agrega baggages al grafo; asigna `currentEdge` al primer `WaitEdge` disponible en el nodo de entrada; publica `SHIPMENT_CREATED`. |
| `RouteSolutionEvent` | `AlnsThread` / `GeneticThread` | Lleva `routes` + `unroutedCount` + `alnsScore`. Para cada ruta: si el primer `FlightEdge` ya partió (`depTime <= clock.now()`), la descarta y deja el baggage en pending para re-enrute (manejo de solución obsoleta en `REAL_TIME`). Las rutas válidas se aplican: mueve de pending a assigned, publica `BAGGAGE_ASSIGNED`. |
| `SimulationEndEvent` | Runner (al init) | Detiene el loop. |

---

### `domain/simulator/feed/`

Interfaces que abstraen la fuente de datos de entrada del simulador. El hilo inyector solo conoce esta interface — no sabe si los datos vienen de un `.txt` o de una API REST.

| Interface | Contrato |
|---|---|
| `ShipmentFeed` | Entrega `ShipmentDataDTO` en orden cronológico. El thread consume hasta que se agota o se interrumpe. |
| `CancellationFeed` | Entrega entradas de cancelación en orden cronológico. |

Implementaciones en `infrastructure/files/` (txt) y `infrastructure/api/` (manual/futuro).

---

### `domain/simulator/thread/`

Hilos que trabajan en paralelo al `SimulationRunner`. Cada clase encapsula la lógica de coordinación de su hilo: qué consume, cuándo actúa, qué inyecta.

| Clase | Daemon | Consume | Produce |
|---|---|---|---|
| `ShipmentInjectorThread` | sí | `ShipmentFeed` + `SimulationClock` | `NewShipmentEvent` vía `runner.submit()` |
| `CancellationInjectorThread` | sí | `CancellationFeed` + `SimulationClock` | `FlightCancelledEvent` vía `runner.submit()` |
| `AlnsThread` | sí | `AlnsProjection` (snapshot del grafo) + `ALNSAlgorithm` | `RouteSolutionEvent` vía `runner.submit()` |
| `GeneticThread` | sí | `GeneticProjection` (snapshot del grafo) + `GeneticAlgorithm` | `RouteSolutionEvent` vía `runner.submit()` |

**Regla de los threads:** ningún hilo aquí escribe directamente en `SpaceTimeGraph`. Todo cambio al grafo llega vía `runner.submit(SimEvent)`.

`ShipmentInjectorThread` y `CancellationInjectorThread` esperan bloqueantes hasta que el tiempo de simulación llegue al tiempo del próximo dato (`waitUntilSimTime`). Respetan la pausa del reloj mediante `effectiveWallTimeMs()`.

`AlnsThread` y `GeneticThread` construyen su snapshot (`AlnsProjection` / `GeneticProjection`) antes de iniciar la optimización. Durante la optimización no acceden al grafo en vivo. Al terminar, envían `RouteSolutionEvent` con el resultado.

---

### `domain/optimizer/`

Algoritmos de optimización e interfaces compartidas.

| Clase | Descripción |
|---|---|
| `RoutingOptimizer` | Interface Strategy: `SolutionResult optimize(GraphProjection projection)`. Implementada por `ALNSAlgorithm` y `GeneticAlgorithm`. |
| `SolutionResult` | Record inmutable: `routes`, `isPartial`, `generatedAt`, `unroutedCount` (baggages que el algoritmo no pudo rutear), `alnsScore` (función objetivo del mejor candidato encontrado). Nunca null — si no hay solución, `isEmpty = true`. |

---

### `domain/optimizer/alns/`

Implementación del algoritmo ALNS (Adaptive Large Neighborhood Search).

| Clase | Descripción |
|---|---|
| `AlnsProjection` | Implementa `GraphProjection`. Snapshot inmutable con lo que ALNS necesita: baggages pendientes, aristas de vuelo disponibles, índice de nodos por aeropuerto. Se construye en `AlnsThread` antes de optimizar. |
| `ALNSAlgorithm` | Implements `RoutingOptimizer`. Ciclo destroy/repair con selección adaptativa por pesos (Roulette Wheel). |
| `DestroyOperator` | Interface: `destroy(BaggageSolution, AlnsProjection) → List<Baggage>`. |
| `RepairOperator` | Interface: `repair(List<Baggage>, AlnsProjection) → void`. |
| `destroy/` | `RandomRemoval`, `ShawRemoval`, `WorstRemoval`, `TimeWindowRemoval`, `OverloadedFlightRemoval`. |
| `repair/` | `GreedyInsertion`, `RegretInsertion`, `MinWaitInsertion`. |
| `RouletteWheelSelector` | Selecciona operador con probabilidad proporcional a su peso acumulado. Actualiza pesos según calidad de solución. |
| `GraspInitializer` | Solución inicial con heurística GRASP para arrancar el ALNS. |
| `AcceptanceCriterion` | Interface: `accept(double newCost, double currentCost) → boolean`. |
| `HillClimbing` | Acepta solo si mejora. |
| `SimulatedAnnealing` | Acepta peores soluciones con probabilidad decreciente. |

---

### `domain/optimizer/genetic/`

Implementación del Algoritmo Genético.

| Clase | Descripción |
|---|---|
| `GeneticProjection` | Implementa `GraphProjection`. Snapshot con lo que el Genético necesita: población inicial, vuelos disponibles, baggages a asignar. |
| `GeneticAlgorithm` | Implements `RoutingOptimizer`. Ciclo de selección, crossover, mutación y reemplazo. |
| `operators/` | Crossover, mutación, selección por torneo o ruleta. |

---

### `domain/util/`

Utilidades puras sin estado ni dependencias externas.

| Clase | Descripción |
|---|---|
| `TimeUtils` | Conversiones `localToUtc`, `utcToLocal`, `combineDateAndLocalTime` (maneja vuelos nocturnos). |

---

## `application`

Orquesta el dominio. No tiene lógica de negocio propia — solo llama a entidades y servicios de dominio, y coordina puertos de entrada/salida.

---

### `application/port/in/`

Interfaces que expone la capa de aplicación hacia `presentation`.

| Interface | Métodos |
|---|---|
| `SimulationControlPort` | `start(simStart, simEnd) → sessionId`, `pause(id)`, `resume(id)`, `stop(id)` |
| `SimulationQueryPort` | `getSession(id)`, `getDashboard(id)`, `getBaggageState(sessionId, baggageId)` |
| `AvailableDaysPort` | `getAvailableDates() → List<LocalDate>` |
| `HistoricalQueryPort` | `getCompletedFlights(range)`, `getDeliveredBaggages(range)` *(pendiente)* |

---

### `application/port/out/`

Interfaces que implementa `infrastructure`. El dominio las consume mediante inyección de dependencias.

| Interface | Implementación | Estado |
|---|---|---|
| `HistoricalFlightRepository` | `PostgreSQLFlightRepository` | pendiente |
| `HistoricalBaggageRepository` | `PostgreSQLBaggageRepository` | pendiente |

> `StatePublisher` vive en `domain/simulator/` porque `SimulationRunner` (dominio) la usa directamente.

---

### `application/dto/`

Records de resultado que los use cases devuelven a la capa de presentación.

| Record | Campos |
|---|---|
| `SimSessionView` | id, status, simTime, simStart, simEnd |
| `DashboardView` | simTime, delivered, pending, assigned, inFlight, slaBreaches, throughputPerHour |
| `BaggageView` | baggageId, status, currentIcao, flightId, destIcao, deadlineUtc |

---

### `application/usecase/`

| Clase | Responsabilidad |
|---|---|
| `SimulationRegistry` | `ConcurrentHashMap<sessionId, SimulationSession>`. Permite hasta dos sesiones simultáneas. `findOrThrow` lanza `IllegalArgumentException` → HTTP 404. |
| `SimulationSession` | Contenedor de una sesión activa: runner, grafo, config, lista de hilos, status `volatile`. `interruptAll()` detiene todos los hilos de forma limpia. |
| `RunSimulationUseCase` | Implementa `SimulationControlPort`. Calcula speedFactor automático (simDuration / 900 s reales objetivo). Construye grafo, instancia feeds y todos los hilos. También implementa pause/resume/stop. |
| `QuerySimulationUseCase` | Implementa `SimulationQueryPort`. Lee en tiempo real del grafo en memoria. Deriva `DashboardView` (métricas agregadas) y `BaggageView` (status desde `currentEdge` + `isUnassigned`). |
| `QueryCurrentStateUseCase` | *(pendiente)* Si `instant >= marginLowerCompleted` → SpaceTimeGraph. Si antes → PostgreSQL. |

---

## `infrastructure`

Implementaciones concretas. Depende del dominio y de application/port/out; no al revés.

---

### `infrastructure/files/`

Adaptadores de lectura de archivos `.txt`. Se usan en dos patrones distintos:

**Carga inicial (una vez al arranque):**

| Clase | Descripción |
|---|---|
| `AirportParser` | Lee `c.1inf54...txt` (UTF-16). Detecta continente por encabezado de sección. Devuelve `Map<String, AirportDataDTO>`. |
| `FlightParser` | Lee `planes_vuelo.txt` (ISO-8859-1). Formato `ORIG-DEST-HH:MM-HH:MM-cap`. Devuelve `List<FlightScheduleDataDTO>`. |

**Feeds de streaming (durante la simulación):**

| Clase | Descripción |
|---|---|
| `ShipmentParser` | Helper interno. Parsea una línea del formato `id-fecha-HH-mm-dest-qty-cliente`. No hace I/O propia. |
| `TxtShipmentFeed` | Implements `ShipmentFeed`. Abre todos los `_envios_XXXX_.txt` del directorio y los fusiona en orden cronológico (k-way merge con `PriorityQueue`). Devuelve `ShipmentDataDTO` de a uno. |
| `CancellationParser` | Helper interno. Parsea una línea del formato `flightKey yyyyMMdd`. |
| `TxtCancellationFeed` | Implements `CancellationFeed`. Lee `cancelaciones.txt` en orden. |

---

### `infrastructure/api/` *(futuro)*

Adaptadores para entrada manual vía API REST. Implementan las mismas interfaces de dominio.

| Clase | Descripción |
|---|---|
| `ApiShipmentFeed` | Implements `ShipmentFeed`. Los datos llegan por REST en lugar de archivo; los encola internamente hasta que el thread los consuma. |
| `ApiCancellationFeed` | Implements `CancellationFeed`. Análogo para cancelaciones. |

---

### `infrastructure/redis/`

Publicación asíncrona del estado hacia el frontend.

| Clase | Descripción |
|---|---|
| `RedisStatePublisher` | Implements `StatePublisher`. Recibe DTOs del runner y los encola en una `BlockingQueue` interna. No bloquea el hilo de simulación. |
| `RedisPublisherThread` | Daemon que drena la `BlockingQueue` y escribe a Redis. Escribe Hashes (estado actual) y el Stream (log de eventos). |
| `RedisConfig` | Configuración del cliente Redis (Lettuce). |

**Estructura de claves Redis:**

```
HASH    airport:{ICAO}          → load, capacity, pendingBaggages
HASH    flight:{edgeId}         → status, load, capacity, depTime, arrTime
HASH    baggage:{id}            → status, currentIcao, destIcao, deadlineUtc
HASH    sim:status              → simTime, paused, delivered, pending, assigned, mode
STREAM  sim:events              → log cronológico de todos los cambios de estado
```

**Eventos publicados al Stream:**

Cada mensaje es **autocontenido**: lleva el estado real observado en ese momento, no una referencia al plan. El frontend no necesita cruzar datos con mensajes anteriores — si la maleta termina en un aeropuerto distinto al planificado, el mensaje lo dice explícitamente.

| Handler que lo origina | Tipo en Stream | Qué contiene |
|---|---|---|
| `HorizonExpandEvent` | `FLIGHT_SCHEDULED` | flightId, fromIcao, toIcao, depTime, capacity |
| `FlightDepartureEvent` | `FLIGHT_DEPARTED` | flightId, fromIcao, toIcao, load, capacity |
| `FlightDepartureEvent` | `BAGGAGE_DEPARTED` | baggageId, flightId, fromIcao, toIcao (estado real; uno por baggage a bordo) |
| `FlightArrivalEvent` | `FLIGHT_ARRIVED` | flightId, toIcao, load |
| `FlightArrivalEvent` | `BAGGAGE_ARRIVED` | baggageId, flightId, currentIcao (en conexión — dónde está ahora realmente) |
| `FlightArrivalEvent` | `BAGGAGE_DELIVERED` | baggageId, currentIcao (llegó a destino) |
| `FlightArrivalEvent` | `BAGGAGE_PENDING` | baggageId, currentIcao (ruta vacía al llegar — re-enrutamiento necesario) |
| `FlightCancelledEvent` | `FLIGHT_CANCELLED` | flightId |
| `FlightCancelledEvent` | `BAGGAGE_PENDING` | baggageId, currentIcao (posición real desde donde se re-enruta) |
| `NewShipmentEvent` | `SHIPMENT_CREATED` | shipmentId, baggageIds, originIcao, destIcao, deadline |
| `RouteSolutionEvent` | `BAGGAGE_ASSIGNED` | baggageId, route (lista de flightIds — plan actualizado) |

**Hash Redis por entidad — siempre estado actual real:**

```
baggage:{id}  →  status        IN_FLIGHT / WAITING / PENDING / DELIVERED
                 currentIcao   SKBO   (fromIcao si IN_FLIGHT, currentIcao si WAITING/PENDING)
                 toIcao        SEQM   (solo si IN_FLIGHT — destino real del tramo en curso)
                 flightId      SKBO-SEQM-19:00-20260102   (solo si IN_FLIGHT)
                 deadlineUtc   2026-01-02T12:00:00Z
```

**Flujo de publicación:**

```
SimulationRunner.handleXxx()
    │  construye StateChangeDTO con datos ya en la pila (sin re-leer el grafo)
    │
    ▼
RedisStatePublisher.publish(dto)     ← offer() a BlockingQueue, O(1), no bloquea
    │
    ▼ (hilo separado)
RedisPublisherThread.run()
    ├─▶ HSET {entidad}:{id}  ...     ← estado actual (para queries puntuales)
    └─▶ XADD sim:events * ...        ← delta en tiempo real (para el mapa en vivo)
```

**Regla clave:** `RedisPublisherThread` nunca lee `SpaceTimeGraph`. El DTO se construye en el hilo del runner con los datos que ya tiene, antes de llamar `publish()`.

---

### `infrastructure/persistence/`

Histórico de entidades ya podadas del rolling horizon.

| Clase | Descripción |
|---|---|
| `PostgreSQLFlightRepository` | Implements `HistoricalFlightRepository`. Persiste vuelos completados antes de que `reduceGraph` los elimine. |
| `PostgreSQLBaggageRepository` | Implements `HistoricalBaggageRepository`. Persiste baggages entregados. |
| `config/DataSourceConfig` | Configuración del datasource. |
| `config/JpaConfig` | Configuración JPA/Hibernate. |

---

### `infrastructure/security/`

Autenticación JWT. Vive en infrastructure porque depende de librerías externas (jjwt, Spring Security).

| Clase | Descripción |
|---|---|
| `JwtService` | Genera tokens HS256 firmados (`generateToken`) y los valida (`isValid`, `extractUsername`). Secret desde `${JWT_SECRET}` (variable de entorno), expiración desde `application.yml`. |
| `JwtAuthFilter` | `OncePerRequestFilter`. Intercepta cada request, extrae el header `Authorization: Bearer <token>`, valida con `JwtService` y registra la autenticación en `SecurityContextHolder`. Logging con SLF4J. |
| `SecurityConfig` | Configuración STATELESS (sin sesión HTTP), CSRF deshabilitado. Security headers HTTP: `X-Frame-Options DENY`, `X-Content-Type-Options nosniff`, HSTS. Rutas públicas: `POST /api/v1/auth/login`, `GET /api/v1/data/available-days`. El resto requiere JWT válido. |
| `UserRepository` | Interface. `DbUserRepository` la implementa leyendo de `public.users` (PostgreSQL, BCrypt hash). `JsonUserRepository` es legacy sin `@Component`, conservado como referencia de seed. |

---

### `infrastructure/config/`

| Clase | Descripción |
|---|---|
| `SpringConfig` | `@Configuration`. Define `@Bean` para `SimulationRegistry`, `RunSimulationUseCase`, `QuerySimulationUseCase` y `TxtAvailableDaysService`. Carga archivos de datos desde classpath usando `getClassLoader().getResource()`. |
| `application.yml` | Puerto 8080. Carga `.env` vía `spring.config.import: optional:file:.env[.properties]`. Rutas de archivos de datos. `jwt.secret` desde `${JWT_SECRET}`. Datasource desde `${DB_URL}`, `${DB_USER}`, `${DB_PASSWORD}`. `app.cors.allowed-origins` configurable. Expiración JWT (1 h). |
| `.env` | Archivo local (`.gitignore`) con `JWT_SECRET`, `DB_PASSWORD`, `DB_URL`, `DB_USER`. En producción se usan variables del sistema operativo; el `.env` es solo para desarrollo local. |

---

## `presentation`

Expone la aplicación hacia el exterior (HTTP, WebSocket). No contiene lógica de negocio — solo traduce entre HTTP y los puertos de application.

Punto de entrada Spring Boot: `TasfApplication` (`@SpringBootApplication` en `com.tasf.b2b`).

---

### `presentation/rest/`

Base URL: `http://host:8080/api/v1`

| Clase | Endpoints | Auth |
|---|---|---|
| `AuthController` | `POST /auth/login` — credenciales → JWT | pública |
| `AuthController` | `POST /auth/refresh` — token actual → nuevo token | JWT |
| `DataController` | `GET /data/available-days` — fechas con datos en los archivos TXT | pública |
| `SimulationController` | `POST /simulations` — crea e inicia sesión; body: `{simStart, simEnd}` | JWT |
| `SimulationController` | `GET /simulations/:id` — status, simTime, rango de fechas | JWT |
| `SimulationController` | `POST /simulations/:id/pause` | JWT |
| `SimulationController` | `POST /simulations/:id/resume` | JWT |
| `SimulationController` | `POST /simulations/:id/stop` | JWT |
| `MonitoringController` | `GET /simulations/:id/dashboard` — métricas en tiempo real | JWT |
| `TrackingController` | `GET /simulations/:id/baggage/:baggageId` — estado de una maleta | JWT |
| `GlobalExceptionHandler` | `IllegalArgumentException` → 404, `IllegalStateException` → 409, `ResponseStatusException` → su propio status (401, 429…), `Exception` → 500 sin detalles internos (ID de referencia en logs). SLF4J. | — |

Los DTOs de request/response son records estáticos anidados dentro de cada controller.

---

### `presentation/websocket/`

Implementación actual: en memoria (sin Redis). Cuando Redis esté disponible, `InMemoryStatePublisher` se reemplaza por `RedisStatePublisher` sin cambiar nada en esta capa.

| Clase | Descripción |
|---|---|
| `InMemoryStatePublisher` | Implements `StatePublisher`. `BlockingQueue` + hilo daemon drenador. Broadcasta cada evento a todas las `WebSocketSession` suscritas a esa sesión de simulación. `close()` interrumpe el drenador. |
| `SimulationWebSocketHandler` | Al conectar: registra la `WebSocketSession` en el `InMemoryStatePublisher` correspondiente. Al desconectar: la desregistra. No tiene hilo propio por conexión. |
| `JwtHandshakeInterceptor` | Valida el JWT vía query param `?token=<jwt>` antes de aceptar el handshake WebSocket. Rechaza la conexión si el token es inválido o ausente. |
| `WebSocketConfig` | Registra el handler en `/api/v1/simulations/{id}/ws`. Orígenes permitidos desde `${app.cors.allowed-origins}`. |

Formato de cada mensaje WebSocket:
```json
{ "type": "BAGGAGE_DEPARTED", "simTime": "...", "payload": {} }
```

---

## Modelo de hilos

```
simulation-runner        (no-daemon)  ← único escritor de SpaceTimeGraph
shipment-injector        (daemon)     ← consume ShipmentFeed, pause-aware
cancellation-injector    (daemon)     ← consume CancellationFeed, pause-aware
alns-thread              (daemon)     ← snapshot → ALNS → RouteSolutionEvent  (isActive=true)
alns-eval-thread         (daemon)     ← snapshot → ALNS → solo métricas        (isActive=false)
genetic-thread           (daemon)     ← snapshot → Genético → RouteSolutionEvent (isActive=true)
genetic-eval-thread      (daemon)     ← snapshot → Genético → solo métricas     (isActive=false)
redis-publisher          (daemon)     ← BlockingQueue → Redis, sin tocar el grafo
```
Los hilos `*-eval-thread` solo se arrancan en los modos `ALNS_ACTIVE_GENETIC_EVAL` y `GENETIC_ACTIVE_ALNS_EVAL`.

Toda comunicación entre hilos pasa por `runner.submit(SimEvent)` o por la `BlockingQueue` del publisher. No hay locks en `SpaceTimeGraph` porque solo un hilo lo escribe.

---

## Modos de timing del optimizador

| Modo | Comportamiento |
|---|---|
| `REAL_TIME` | El thread optimizador corre en paralelo. Si la solución llega después de que un vuelo partió, ese tramo se descarta y el baggage queda en pending para re-enrute en la siguiente iteración. El porcentaje de rutas obsoletas es inversamente proporcional al `SPEED_FACTOR` — a mayor velocidad, más obsolescencia. |
| `PAUSE` | El runner pausa el reloj antes de lanzar el optimizador; reanuda al recibir `RouteSolutionEvent`. Garantiza soluciones sin obsolescencia. *(pendiente de implementar)* |
| `EVENT_DRIVEN` | Sin reloj de pared. El runner avanza de evento en evento; el optimizador calcula entre cada par. *(pendiente de implementar)* |

---

## Routing de consultas de estado

```
Si instant ∈ [marginLowerCompleted, horizonCompleted]  →  SpaceTimeGraph en memoria
Si instant < marginLowerCompleted                       →  PostgreSQL (histórico)
```

El `QueryCurrentStateUseCase` implementa esta lógica de enrutamiento. La capa de presentación no necesita saber cuál fuente se usa.

---

## Dos instancias de simulación

Pueden correr simultáneamente con sus propios grafos, hilos y configuraciones.

| | Simulador configurable | Simulador tiempo real |
|---|---|---|
| `DataSource` | `TXT` — `TxtShipmentFeed` + `TxtCancellationFeed` | `MANUAL` — `ApiShipmentFeed` + `ApiCancellationFeed` |
| Speed factor | Configurable | 1x |
| Fechas | Configurables | Fecha actual del sistema |
| Uso | Testing, análisis histórico | Operación en producción |

`RunSimulationUseCase` decide qué implementación de `ShipmentFeed` / `CancellationFeed` inyecta según el `DataSource` del `SimulationConfig`.
