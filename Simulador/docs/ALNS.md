# Optimizador ALNS (Adaptive Large Neighborhood Search)

## Qué es y para qué sirve

El ALNS es el metaheurístico principal que asigna maletas pendientes a rutas de vuelos disponibles. Dado un snapshot del grafo espacio-temporal, busca la asignación que minimice (en orden de prioridad):

1. **Número de maletas sin ruta** (objetivo primario)
2. **Tardanza media de las maletas rutadas** (objetivo secundario)

El algoritmo es *adaptativo*: los operadores de destrucción y reparación ajustan automáticamente sus probabilidades de selección según su rendimiento reciente.

---

## Arquitectura general

```
AlnsThread (hilo separado, se dispara cada ~200 ms)
  └─► AlnsProjectionBuilder.build()   → AlnsProjection (snapshot inmutable)
  └─► ALNSAlgorithm.optimize()        → SolutionResult
  └─► RouteSolutionEvent              → SimulationRunner (aplica rutas al grafo vivo)
```

El ALNS trabaja siempre sobre un **snapshot inmutable** del grafo; nunca toca el grafo vivo directamente. Esto permite que el simulador siga avanzando mientras el optimizador trabaja.

---

## Ciclo principal del algoritmo

```
Inicialización (GraspInitializer):
  Ejecuta 2 arranques greedy y elige el mejor como solución inicial.

BUCLE mientras tiempo < TIME_BUDGET_MS (175 ms):
  1. Clonar solución actual → candidata
  2. Seleccionar operador de DESTRUCCIÓN (ruleta adaptativa)
     Eliminar k maletas rutadas (k ≈ tamaño_pendientes / 5 … / 3)
  3. Seleccionar operador de REPARACIÓN (ruleta adaptativa)
     Reinsertar todas las maletas desalojadas + las que ya estaban sin ruta
  4. Evaluar score de la candidata
  5. Decisión de aceptación (Simulated Annealing):
     - Candidata mejor que best  → best ← candidata; current ← candidata; recompensa 3.0
     - SA acepta (aunque sea peor) → current ← candidata; recompensa 1.0
     - Rechazada                  → sin cambio; recompensa 0.0
  6. Actualizar pesos de los operadores usados
  7. Reducir temperatura: T ← T × COOLING_RATE
```

---

## Parámetros configurables

Todos los valores están como constantes en `ALNSAlgorithm.java` salvo donde se indica.

| Parámetro | Valor actual | Qué controla |
|---|---|---|
| `TIME_BUDGET_MS` | 175 ms | Tiempo máximo por ejecución del optimizador |
| `INITIAL_TEMP` | 20.0 | Temperatura inicial de Simulated Annealing |
| `COOLING_RATE` | 0.90 | Factor de enfriamiento por iteración: `T_nueva = T × 0.90` |
| `REWARD_NEW_BEST` | 3.0 | Recompensa al operador si encuentra nuevo óptimo global |
| `REWARD_ACCEPTED` | 1.0 | Recompensa al operador si SA acepta la solución |
| `REWARD_REJECTED` | 0.0 | Recompensa al operador si SA rechaza |
| `DECAY_FACTOR` | 0.8 | En `RouletteWheelSelector`: `w ← 0.8×w_ant + 0.2×recompensa` |
| `REGRET_LIMIT` | 8 maletas | En `RegretInsertion`: si hay más de 8 sin ruta, cae a Greedy |
| `MAX_HOPS` | 8 vuelos | Máximo de vuelos en una ruta (en `RouteFinder`) |
| `minConnectionMinutes` | 10 min | Tiempo mínimo de conexión entre vuelos (`SimulationConfig`) |
| `pickupMinutes` | 10 min | Tiempo de recogida en aeropuerto de origen (`SimulationConfig`) |

### Ajuste del tamaño de destrucción `k`

```java
baseK = max(1, pendientes.size() / 5)
maxK  = max(1, pendientes.size() / 3)
k     = min(maxK, max(baseK, sin_ruta.size()))
```

Si hay muchas maletas sin ruta, `k` sube automáticamente para ser más agresivo.

---

## Simulated Annealing (criterio de aceptación)

```
Si candidata ≤ actual  → aceptar siempre; T ← T × cooling
Si candidata > actual  → aceptar con P = exp(-Δ / T); T ← T × cooling
```

Con `T₀ = 20` y `cooling = 0.90`:

| Iteración | Temperatura | P de aceptar Δ=1 | P de aceptar Δ=5 |
|---|---|---|---|
| 0 | 20.0 | 95 % | 78 % |
| 10 | ~7.0 | 87 % | 49 % |
| 30 | ~0.8 | 29 % | 0.1 % |

La temperatura baja **en cada iteración**, tanto si se acepta como si se rechaza.

---

## Función de puntuación

```
score = (sin_ruta.size() × 2.0) + promedio(costo_i para cada rutada)

costo_i = 1.0 - ratio_i
ratio_i = (deadline_i - arrivalTime_i) / (deadline_i - availableFrom_i)
```

- `ratio = 1` → llega en el momento óptimo → costo = 0
- `ratio = 0` → llega justo en el deadline → costo = 1
- `ratio < 0` → llega tarde → costo > 1

El factor `2.0` por maleta sin ruta garantiza que siempre sea preferible rutar aunque sea tarde:
`K × 2.0 > (K-1) × 2.0 + 1.0` ✓ para cualquier K.

---

## Operadores de destrucción

Cada operador elimina `k` maletas rutadas para permitir su reinserción:

| Operador | Estrategia |
|---|---|
| `RandomRemoval` | Elige `k` maletas al azar. Exploración genérica. |
| `ShawRemoval` | Elige maletas *similares* entre sí (mismo origen/destino, deadline próximo, vuelo compartido). Útil para reorganizar grupos localmente. |
| `WorstRemoval` | Elimina las `k` maletas con peor ratio deadline (las más tardías). Ataca directamente el objetivo. |
| `TimeWindowRemoval` | Elige una ventana temporal aleatoria de 2 h y elimina maletas en ese rango. Libera capacidad en un período concreto. |
| `OverloadedFlightRemoval` | Encuentra el vuelo con menos capacidad libre y elimina `k` maletas que lo usan. Descongestion. |
| `UnroutedRelatedRemoval` | Si hay maletas sin ruta, elimina rutadas que compiten por los mismos aeropuertos. Libera espacio para las bloqueadas. |

**Similitud en ShawRemoval** (hasta 4 puntos):
- +1 mismo ICAO de origen
- +1 mismo ICAO de destino
- +1 deadline dentro de 4 horas entre sí
- +1 comparten al menos un vuelo en su ruta

---

## Operadores de reparación

Cada operador intenta enrutar todas las maletas en `solution.unrouted()`:

| Operador | Estrategia |
|---|---|
| `GreedyInsertion` | Ordena por deadline urgente primero; asigna cada maleta a la mejor ruta disponible (mínimo costo). |
| `MinWaitInsertion` | Igual que Greedy pero usa `findRouteMinHops()`: prefiere rutas con menos escalas aunque lleguen un poco más tarde. |
| `RegretInsertion` | Calcula la diferencia entre la 1ª y 2ª mejor ruta de cada maleta (*regret*); inserta primero quien más pierde si no se inserta ahora. Cae a Greedy si hay más de 8 sin ruta. |

### RouteFinder (búsqueda de rutas)

Usa Dijkstra sobre el grafo espacio-temporal con las siguientes restricciones:
- Respeta ventanas de conexión (`minConnectionMinutes`)
- Respeta deadlines de las maletas
- Respeta capacidad de los vuelos
- Máximo 8 vuelos por ruta

Dos variantes:
- `findRoute()` → minimiza tiempo de llegada
- `findRouteMinHops()` → minimiza número de escalas

---

## Selección adaptativa de operadores (Ruleta ponderada)

```
Peso inicial de cada operador: w = 1.0

Después de cada uso:
  w_nuevo = DECAY_FACTOR × w_viejo + (1 - DECAY_FACTOR) × recompensa
           = 0.8 × w_viejo + 0.2 × recompensa

Probabilidad de selección: P(op_i) = w_i / Σ w_j

Piso: w ≥ 0.01  (ningún operador llega a 0)
```

El factor de decay `0.8` da más peso a la experiencia reciente que a la histórica.

---

## Estructura de archivos

```
optimizer/alns/
├── ALNSAlgorithm.java              ← Bucle principal, SA, selección de operadores
├── BaggageSolution.java            ← Solución mutable (routes + unrouted)
├── BaggageState.java               ← Estado inmutable de una maleta
├── FlightSnapshot.java             ← Snapshot inmutable de un vuelo
├── AlnsProjection.java             ← Snapshot del problema (entrada al algoritmo)
├── AlnsProjectionBuilder.java      ← Construye el snapshot desde el grafo vivo
├── RouteFinder.java                ← Búsqueda Dijkstra de rutas
├── GraspInitializer.java           ← Inicialización greedy (2 arranques)
├── RouletteWheelSelector.java      ← Ruleta ponderada adaptativa
├── DestroyOperator.java            ← Interfaz
├── RepairOperator.java             ← Interfaz
├── AcceptanceCriterion.java        ← Interfaz
├── destroy/
│   ├── RandomRemoval.java
│   ├── ShawRemoval.java
│   ├── WorstRemoval.java
│   ├── TimeWindowRemoval.java
│   ├── OverloadedFlightRemoval.java
│   └── UnroutedRelatedRemoval.java
├── repair/
│   ├── GreedyInsertion.java
│   ├── RegretInsertion.java
│   └── MinWaitInsertion.java
└── acceptance/
    ├── SimulatedAnnealing.java
    └── HillClimbing.java           ← Alternativa (solo acepta mejoras)

optimizer/thread/
└── AlnsThread.java                 ← Hilo ejecutor (disparo cada ~200 ms)
```

---

## Modos de operación

Configurados en `SimulationConfig.OptimizerMode`:

| Modo | Descripción |
|---|---|
| `ALNS_ONLY` | Solo ALNS activo |
| `GENETIC_ONLY` | Solo algoritmo genético activo |
| `ALNS_ACTIVE_GENETIC_EVAL` | ALNS aplica rutas; GA evalúa sin aplicar |
| `GENETIC_ACTIVE_ALNS_EVAL` | GA aplica rutas; ALNS evalúa sin aplicar |

---

## Métricas publicadas por WebSocket

Tras cada ejecución, `AlnsThread` publica:
- Tiempo de ejecución (ms)
- Score de la mejor solución encontrada
- Número de maletas sin ruta
- Costo medio de las maletas rutadas
