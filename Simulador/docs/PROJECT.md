# Proyecto — Simulador de Enrutamiento de Carga TASF

## Problema

Enrutar **baggages** (unidades de carga) a través de una red de aeropuertos conectados por vuelos comerciales, respetando capacidades y plazos de entrega, y re-enrutando automáticamente ante cancelaciones de vuelos.

---

## Entidades principales

| Entidad | Paquete | Descripción |
|---|---|---|
| `AirportDataDTO` | `domain/model/graph/immovable` | Aeropuerto: ICAO, ciudad, país, continente, GMT offset, capacidad, coordenadas |
| `FlightScheduleDataDTO` | `domain/model/graph/immovable` | Vuelo diario recurrente: origen, destino, hora local salida/llegada, capacidad. ID: `ORIG-DEST-HH:mm` |
| `ShipmentDataDTO` | `domain/model/graph/immovable` | Pedido de cliente: origen, destino, hora de entrada (UTC), cantidad de baggages, cliente |
| `DeliveryType` | `domain/model/graph/immovable` | Enum: `INTRACONTINENTAL` / `INTERCONTINENTAL` |
| `DeliveryTypeValue` | `domain/model/graph/immovable` | Duración máxima por tipo: 12 h / 24 h |
| `DeliveryTypeValues` | `domain/model/graph/immovable` | Mapa tipo → valor; determina el tipo comparando continentes |
| `Shipment` | `domain/model/graph/movable` | Instancia viva de un pedido; genera sus `Baggage` hijos |
| `Baggage` | `domain/model/graph/movable` | Unidad que viaja. Siempre vive sobre una arista: `currentEdge` es `WaitEdge` cuando espera en un aeropuerto y `FlightEdge` cuando está volando. Tiene `expectedRoute` (FlightEdges planificados) y `routeTraveled` (FlightEdges recorridos). |
| `SpaceTimeGraph` | `domain/model/graph` | Grafo espacio-temporal con rolling horizon |
| `STNode` | `domain/model/graph/componentsgraph` | Punto `(aeropuerto, instante UTC)` |
| `FlightEdge` | `domain/model/graph/componentsgraph` | Arista de vuelo entre dos `STNode` |
| `WaitEdge` | `domain/model/graph/componentsgraph` | Arista de espera en el mismo aeropuerto |

---

## Reglas de negocio

### Plazos de entrega
- **INTRACONTINENTAL**: mismo continente → plazo máximo **12 h** desde la hora de entrada.
- **INTERCONTINENTAL**: continentes distintos → plazo máximo **24 h** desde la hora de entrada.
- El continente se determina por el encabezado de sección del archivo de aeropuertos (`America del Sur`, `Europa`, `Asia`).

### Tiempos de conexión y recogida *(parámetros configurables en `SimulationConfig`)*
- **`minConnectionMinutes`** (default: 10 min): tiempo mínimo que una maleta debe esperar en un aeropuerto de conexión después de que llega su vuelo antes de poder embarcar en el siguiente. El optimizador no puede asignar un vuelo de conexión que sale antes de `arrivalTime + minConnectionMinutes`.
- **`pickupMinutes`** (default: 10 min): tiempo mínimo desde que la maleta llega a su aeropuerto destino hasta que se considera entregada. Modela el tiempo de recogida en destino.

### Asignación de rutas
- Un baggage sin ruta asignada queda en `pendingBaggages` (ordenado por deadline, más urgente primero).
- Un baggage con ruta asignada queda en `assignedBaggages`.
- Al cumplir el tiempo de recogida en destino pasa a entregado (histórico en PostgreSQL).
- **La asignación de baggages a vuelos la determina siempre el optimizador activo** (ALNS o Genético). El runner solo aplica la solución recibida.

### Cancelaciones
- Se puede cancelar un vuelo **en cualquier momento antes de su salida** (`depTimeUtc > simTime actual`).
- **No se puede cancelar un vuelo que ya partió.**
- No hay margen de antelación obligatorio.

---

## Casos de uso del sistema

### 1. Simulador con datos históricos (.txt)
- Carga aeropuertos y vuelos desde archivos al inicio (una vez).
- Inyecta envíos y cancelaciones desde `.txt` conforme avanza el tiempo de simulación.
- El usuario configura: **fecha inicio**, **fecha fin** y **speed factor**.
- Permite múltiples corridas con distintos parámetros.

### 2. Simulador en tiempo real (entrada manual)
- Los envíos y cancelaciones se ingresan manualmente vía API (formulario o REST).
- Corre en tiempo real (speed factor = 1x).
- Modela el sistema en producción.

Ambos simuladores pueden correr **simultáneamente** e independientemente, cada uno con su propio `SpaceTimeGraph` y sus propios hilos.

### 3. Visualización en tiempo real
- Un frontend muestra el estado actual de vuelos y aeropuertos en tiempo real.
- Se puede hacer consulta individual sobre vuelo, aeropuerto o baggage.
- Datos actuales desde Redis (read model asíncrono).
- Datos históricos (fuera del horizonte rodante) desde PostgreSQL.

### 4. Consultas históricas
- Consultar vuelos completados, baggages entregados, métricas de desempeño del optimizador.
- Routing automático: si el dato está en el horizonte actual → SpaceTimeGraph; si ya fue podado → PostgreSQL.

---

## Publicación de estado hacia el frontend

El `SimulationRunner` no escribe a Redis directamente. Tras cada handler, construye un DTO inmutable con los datos ya procesados y lo entrega a `StatePublisher`. La implementación (`RedisStatePublisher`) encola el DTO en una `BlockingQueue`; un daemon (`RedisPublisherThread`) lo drena y escribe a Redis sin bloquear la simulación.

Redis mantiene dos estructuras:
- **Hashes** (`flight:{id}`, `baggage:{id}`, `airport:{icao}`, `sim:status`) — estado actual por entidad, para queries puntuales.
- **Stream** (`sim:events`) — log cronológico de todos los cambios, para el mapa en vivo vía WebSocket.

El frontend que se conecta en frío primero lee los Hashes y luego se suscribe al Stream. El Stream permite replay al reconectar.

---

## Modos de ejecución del optimizador

| Modo | Comportamiento |
|---|---|
| **REAL_TIME** | El optimizador corre en paralelo. Si tarda más que el tiempo hasta la próxima salida, ese vuelo parte sin solución. |
| **PAUSE** | La simulación pausa el reloj mientras el optimizador calcula. Reanuda al recibir la solución. |
| **EVENT_DRIVEN** | Sin reloj de pared. La simulación avanza solo cuando ocurre un evento; el optimizador calcula entre eventos. |

---

## Optimizadores

- **ALNS** y **Algoritmo Genético** son intercambiables mediante la interface `RoutingOptimizer`.
- Cada uno trabaja sobre su propia proyección (`AlnsProjection`, `GeneticProjection`), snapshots inmutables del grafo construidos por su hilo antes de optimizar.
- Ambos producen el mismo formato de salida: `SolutionResult` — siempre presente, aunque esté vacío o parcial.
- Se configura cuál es el **activo** (define rutas futuras) y cuál el **de evaluación** (corre en paralelo para medir factibilidad comparativa).

---

## Datos de entrada

| Archivo | Formato | Codificación | Cuándo se carga |
|---|---|---|---|
| `c.1inf54...txt` | Secciones por continente, aeropuertos con coords | UTF-16 | Una vez al inicio |
| `planes_vuelo.txt` | `ORIG-DEST-HH:MM-HH:MM-capacidad` | ISO-8859-1 | Una vez al inicio |
| `_envios_XXXX_.txt` | `id-fecha-HH-mm-dest-qty-cliente` (uno por aeropuerto) | ISO-8859-1 | Streaming durante la simulación |
| `cancelaciones.txt` | `flightKey  yyyyMMdd` | ISO-8859-1 | Streaming, en el instante indicado |

En el simulador tiempo real, envíos y cancelaciones se ingresan vía API (reemplaza los `.txt`).

---

## Estructura de archivos

```
com.tasf.b2b/src/main/java/com/tasf/b2b/
│
├── SimulationMain.java                                        [HECHO]
│
├── domain/
│   ├── model/
│   │   └── graph/
│   │       ├── SpaceTimeGraph.java                            [HECHO]
│   │       ├── componentsgraph/
│   │       │   ├── STNode.java                                [HECHO]
│   │       │   ├── STEdge.java                                [HECHO]
│   │       │   ├── FlightEdge.java                            [HECHO]
│   │       │   └── WaitEdge.java                              [HECHO]
│   │       ├── immovable/
│   │       │   ├── AirportDataDTO.java                        [HECHO]
│   │       │   ├── FlightScheduleDataDTO.java                 [HECHO]
│   │       │   ├── ShipmentDataDTO.java                       [HECHO]
│   │       │   ├── DeliveryType.java                          [HECHO]
│   │       │   ├── DeliveryTypeValue.java                     [HECHO]
│   │       │   └── DeliveryTypeValues.java                    [HECHO]
│   │       ├── movable/
│   │       │   ├── Baggage.java                               [HECHO]
│   │       │   └── Shipment.java                              [HECHO]
│   │       └── projection/
│   │           └── GraphProjection.java                       [HECHO]
│   │
│   ├── simulator/
│   │   ├── SimulationRunner.java                              [HECHO]
│   │   ├── SimulationClock.java                               [HECHO]
│   │   ├── SimulationConfig.java                              [HECHO]
│   │   ├── StatePublisher.java                                [HECHO]
│   │   ├── event/
│   │   │   ├── SimEvent.java                                  [HECHO]
│   │   │   ├── HorizonExpandEvent.java                        [HECHO]
│   │   │   ├── FlightDepartureEvent.java                      [HECHO]
│   │   │   ├── FlightArrivalEvent.java                        [HECHO]
│   │   │   ├── FlightCancelledEvent.java                      [HECHO]
│   │   │   ├── NewShipmentEvent.java                          [HECHO]
│   │   │   ├── RouteSolutionEvent.java                        [HECHO]
│   │   │   └── SimulationEndEvent.java                        [HECHO]
│   │   ├── feed/
│   │   │   ├── ShipmentFeed.java                              [HECHO]
│   │   │   └── CancellationFeed.java                          [HECHO]
│   │   └── thread/
│   │       ├── ShipmentInjectorThread.java                    [HECHO]
│   │       ├── CancellationInjectorThread.java                [HECHO]
│   │       ├── AlnsThread.java                                [HECHO]
│   │       └── GeneticThread.java                             [HECHO]
│   │
│   ├── optimizer/
│   │   ├── RoutingOptimizer.java                              [HECHO]
│   │   ├── SolutionResult.java                                [HECHO]
│   │   ├── alns/
│   │   │   ├── AlnsProjection.java                            [HECHO]
│   │   │   ├── ALNSAlgorithm.java                             [HECHO]
│   │   │   ├── GraspInitializer.java                          [HECHO]
│   │   │   ├── acceptance/
│   │   │   │   ├── AcceptanceCriterion.java                   [HECHO]
│   │   │   │   ├── HillClimbing.java                          [HECHO]
│   │   │   │   └── SimulatedAnnealing.java                    [HECHO]
│   │   │   ├── selector/
│   │   │   │   └── RouletteWheelSelector.java                 [HECHO]
│   │   │   └── operators/
│   │   │       ├── DestroyOperator.java                       [HECHO]
│   │   │       ├── RepairOperator.java                        [HECHO]
│   │   │       ├── destroy/
│   │   │       │   ├── RandomRemoval.java                     [HECHO]
│   │   │       │   ├── ShawRemoval.java                       [HECHO]
│   │   │       │   ├── WorstRemoval.java                      [HECHO]
│   │   │       │   ├── TimeWindowRemoval.java                 [HECHO]
│   │   │       │   └── OverloadedFlightRemoval.java           [HECHO]
│   │   │       └── repair/
│   │   │           ├── GreedyInsertion.java                   [HECHO]
│   │   │           ├── RegretInsertion.java                   [HECHO]
│   │   │           └── MinWaitInsertion.java                  [HECHO]
│   │   └── genetic/
│   │       ├── GeneticProjection.java                         [HECHO]
│   │       ├── GeneticAlgorithm.java                          [PENDIENTE — stub]
│   │       └── operators/                                     [PENDIENTE]
│   │
│   └── util/
│       └── TimeUtils.java                                     [HECHO]
│
├── application/
│   ├── port/
│   │   ├── in/
│   │   │   ├── SimulationControlPort.java                     [HECHO]
│   │   │   ├── SimulationQueryPort.java                       [HECHO]
│   │   │   ├── AvailableDaysPort.java                         [HECHO]
│   │   │   └── HistoricalQueryPort.java                       [PENDIENTE]
│   │   └── out/
│   │       ├── HistoricalFlightRepository.java                [PENDIENTE]
│   │       └── HistoricalBaggageRepository.java               [PENDIENTE]
│   ├── usecase/
│   │   ├── SimulationRegistry.java                            [HECHO]
│   │   ├── SimulationSession.java                             [HECHO]
│   │   ├── RunSimulationUseCase.java                          [HECHO]
│   │   ├── QuerySimulationUseCase.java                        [HECHO]
│   │   └── QueryCurrentStateUseCase.java                      [PENDIENTE]
│   └── dto/
│       ├── SimSessionView.java                                [HECHO]
│       ├── DashboardView.java                                 [HECHO]
│       ├── BaggageView.java                                   [HECHO]
│       └── SnapshotView.java                                  [HECHO]
│
├── infrastructure/
│   ├── files/
│   │   ├── AirportParser.java                                 [HECHO]
│   │   ├── FlightParser.java                                  [HECHO]
│   │   ├── ShipmentParser.java                                [HECHO]
│   │   ├── CancellationParser.java                            [HECHO]
│   │   ├── TxtShipmentFeed.java                               [HECHO]
│   │   └── TxtCancellationFeed.java                           [HECHO]
│   ├── api/
│   │   ├── ApiShipmentFeed.java                               [PENDIENTE]
│   │   └── ApiCancellationFeed.java                           [PENDIENTE]
│   ├── redis/
│   │   ├── RedisStatePublisher.java                           [PENDIENTE]
│   │   ├── RedisPublisherThread.java                          [PENDIENTE]
│   │   └── RedisConfig.java                                   [PENDIENTE]
│   ├── persistence/
│   │   ├── entity/                                            [HECHO]
│   │   ├── repository/                                        [HECHO]
│   │   ├── adapter/
│   │   │   ├── DbUserRepository.java                          [HECHO]
│   │   │   ├── DbAvailableDaysService.java                    [HECHO]
│   │   │   ├── DbSimulationShipmentFeed.java                  [HECHO]
│   │   │   ├── DbSimulationCancellationFeed.java              [HECHO]
│   │   │   ├── PostgreSQLFlightRepository.java                [PENDIENTE]
│   │   │   └── PostgreSQLBaggageRepository.java               [PENDIENTE]
│   ├── security/
│   │   ├── JwtService.java                                    [HECHO]
│   │   ├── JwtAuthFilter.java                                 [HECHO]
│   │   ├── SecurityConfig.java                                [HECHO]
│   │   ├── UserRepository.java                                [HECHO]
│   │   └── JsonUserRepository.java                            [HECHO — legacy sin @Component]
│   └── config/
│       └── SpringConfig.java                                  [HECHO]
│
└── presentation/
    ├── rest/
    │   ├── AuthController.java                                [HECHO]
    │   ├── SimulationController.java                          [HECHO]
    │   ├── MonitoringController.java                          [HECHO]
    │   ├── TrackingController.java                            [HECHO]
    │   ├── DataController.java                                [HECHO]
    │   └── GlobalExceptionHandler.java                        [HECHO]
    └── websocket/
        ├── InMemoryStatePublisher.java                        [HECHO]
        ├── SimulationWebSocketHandler.java                    [HECHO]
        ├── JwtHandshakeInterceptor.java                       [HECHO]
        └── WebSocketConfig.java                               [HECHO]
```

---

## Stack tecnológico

| Componente | Tecnología |
|---|---|
| Lenguaje | Java 21 |
| Build | Maven |
| Algoritmos de optimización | ALNS + Genético (mismo JVM, configurables) |
| Estado actual → frontend | Redis — Hashes (estado) + Stream (eventos) |
| Histórico (datos podados) | PostgreSQL |
| API / frontend | Spring Boot REST + WebSocket |
