# API Testing — TASF Simulator

Base URL: `http://localhost:8080/api/v1`

**Estado:**
- ✅ Funciona
- ❌ Falla
- ⏳ Sin probar
- 🔴 No implementado

---

## Resumen de estado

| Estado | Método | Endpoint | Notas |
|--------|--------|----------|-------|
| ✅ | POST | `/auth/login` | |
| ✅ | POST | `/auth/refresh` | URL correcta: `/api/v1/auth/refresh` |
| ✅ | POST | `/admin/airports?mode=replace` | archivo UTF-16 |
| ⏳ | POST | `/admin/airports?mode=merge` | |
| ✅ | POST | `/admin/flights?mode=replace` | archivo ISO-8859-1, requiere aeropuertos |
| ⏳ | POST | `/admin/flights?mode=merge` | |
| ✅ | POST | `/admin/shipments` | varios archivos, 409 si ya hay datos |
| ⏳ | POST | `/admin/shipments/file` | un solo archivo |
| ⏳ | DELETE | `/admin/shipments` | borra todo el histórico |
| ✅ | GET | `/admin/historical` | lista aeropuertos con datos cargados |
| ✅ | GET | `/admin/historical/:icao` | detalle por aeropuerto |
| ✅ | POST | `/admin/historical/:icao?mode=merge` | sube `_envios_ICAO_.txt` |
| ⏳ | DELETE | `/admin/historical/:icao` | borra histórico de un aeropuerto |
| ⏳ | DELETE | `/admin/historical` | borra todo el histórico |
| ✅ | GET | `/data/available-days` | |
| ✅ | GET | `/data/airports` | referencia estática, sin carga en tiempo real |
| ✅ | GET | `/data/routes` | |
| ✅ | POST | `/simulations` | crea e inicia sesión; 409 si el usuario ya tiene una activa |
| ✅ | GET | `/simulations/mine` | sesión activa del usuario autenticado |
| ✅ | GET | `/simulations/:id` | estado de la sesión |
| ✅ | POST | `/simulations/:id/pause` | 409 si no está running |
| ✅ | POST | `/simulations/:id/resume` | 409 si no está paused |
| ✅ | POST | `/simulations/:id/stop` | libera recursos |
| ⏳ | POST | `/simulations/:id/disruptions` | inyecta cancelación/avería |
| ✅ | GET | `/simulations/:id/dashboard` | métricas en tiempo real |
| ✅ | GET | `/simulations/:id/snapshot` | estado completo |
| 🔴 | GET | `/simulations/:id/flights` | lista vuelos con occupancyLevel — **NO IMPLEMENTADO** |
| 🔴 | GET | `/simulations/:id/flights/:flightId` | detalle de vuelo con envíos a bordo — **NO IMPLEMENTADO** |
| 🔴 | GET | `/simulations/:id/airports` | aeropuertos con carga en tiempo real — **NO IMPLEMENTADO** |
| 🔴 | GET | `/simulations/:id/airports/:icao/inbound` | vuelos planificados entrantes — **NO IMPLEMENTADO** |
| 🔴 | GET | `/simulations/:id/airports/:icao/outbound` | vuelos planificados salientes — **NO IMPLEMENTADO** |
| 🔴 | GET | `/simulations/:id/airports/:icao/transit` | maletas esperando conexión — **NO IMPLEMENTADO** |
| 🔴 | GET | `/simulations/:id/shipments/planned` | envíos con ruta asignada — **NO IMPLEMENTADO** |
| 🔴 | GET | `/simulations/:id/shipments/in-flight` | envíos en el aire — **NO IMPLEMENTADO** |
| 🔴 | GET | `/simulations/:id/shipments/delivered?hours=N` | entregas recientes — **NO IMPLEMENTADO** |
| ⏳ | GET | `/simulations/:id/baggage/:baggageId` | tracking de una maleta |
| ⏳ | GET | `/simulations/:id/baggage/:baggageId/route` | ruta completa de una maleta |
| 🔴 | GET | `/simulations/:id/reports/summary` | resumen final de simulación — **NO IMPLEMENTADO** |

---

## 1. Auth

### POST /auth/login — ✅

El campo es `passwordHash` — el front nunca envía la contraseña en texto plano.
El front hashea con SHA-256 antes de enviar: `sha256(password)` usando `crypto.subtle` (Web Crypto API).
El back almacena `bcrypt(sha256(password))` en BD.

Body (`application/json`):
```json
{ "username": "admin", "passwordHash": "<sha256 hex de la contraseña>" }
```

Response `200`:
```json
{ "accessToken": "<jwt>", "expiresAt": "2026-01-02T01:00:00Z" }
```

Errores: `401` credenciales inválidas · `429` más de 5 intentos/minuto por IP

> Para regenerar el hash de BD usar `HashGenerator.java` — genera `bcrypt(sha256(password))` y el SQL de inserción/actualización.

---

### POST /auth/refresh — ✅

Header: `Authorization: Bearer <token>` · Body: ninguno

Response `200`:
```json
{ "accessToken": "<jwt>", "expiresAt": "2026-01-02T02:00:00Z" }
```

> Todos los endpoints siguientes requieren `Authorization: Bearer <token>`.

---

## 2. Admin — Datos de referencia

### POST /admin/airports?mode=replace|merge — replace ✅ / merge ⏳

Request: `multipart/form-data` → campo `file` (archivo **UTF-16**)

Response `200`:
```json
{ "message": "Aeropuertos actualizados (merge)", "count": 30, "errors": [], "warnings": [] }
```

---

### POST /admin/flights?mode=replace|merge — replace ✅ / merge ⏳

Request: `multipart/form-data` → campo `file` (archivo **ISO-8859-1**)  
**Requiere aeropuertos cargados.**

Response `200`:
```json
{ "message": "Vuelos actualizados (merge)", "count": 2866, "errors": [], "warnings": [] }
```

---

## 3. Admin — Shipments (carga bulk)

### POST /admin/shipments — ✅

Sube **varios** `_envios_ICAO_.txt` a la vez.  
Request: `multipart/form-data` → campo `files`

Response `201`:
```json
{ "message": "Shipments cargados y ordenados por entry_utc", "count": 12450, "errors": [], "warnings": [] }
```

Errores: `409` ya existen shipments (hacer `DELETE /admin/shipments` primero)

---

### POST /admin/shipments/file — ⏳

Sube **un solo** archivo.  
Request: `multipart/form-data` → campo `file`

Response `201`:
```json
{ "message": "Shipments cargados desde _envios_SKBO_.txt", "count": 1523, "errors": [], "warnings": [] }
```

---

### DELETE /admin/shipments — ⏳

Response `200`:
```json
{ "message": "Shipments eliminados", "count": 12450, "errors": [], "warnings": [] }
```

---

## 4. Admin — Histórico por aeropuerto

### GET /admin/historical — ✅

Response `200`:
```json
[ { "icao": "SKBO", "count": 1523 }, { "icao": "SEQM", "count": 874 } ]
```

---

### GET /admin/historical/:icao — ✅

Response `200`:
```json
{
  "icao": "SKBO",
  "total": 1523,
  "byDay": [
    { "date": "2026-01-02", "count": 412 },
    { "date": "2026-01-03", "count": 589 }
  ]
}
```

Errores: `404` aeropuerto no registrado

---

### POST /admin/historical/:icao?mode=merge|replace — merge ✅ / replace ⏳

Request: `multipart/form-data` → campo `file` (nombre debe contener el ICAO)

Response `201`:
```json
{ "message": "Histórico de SKBO actualizado (merge)", "count": 1523, "errors": [], "warnings": [] }
```

Errores: `400` nombre de archivo no coincide con el ICAO del path

---

### DELETE /admin/historical/:icao — ⏳

Response `200`:
```json
{ "message": "Histórico de SKBO eliminado", "count": 1523, "errors": [], "warnings": [] }
```

---

### DELETE /admin/historical — ⏳

Response `200`:
```json
{ "message": "Todo el histórico eliminado", "count": 12450, "errors": [], "warnings": [] }
```

---

## 5. Datos disponibles

### GET /data/available-days — ✅

Header: `Authorization: Bearer <token>`

Response `200`:
```json
{ "availableDates": ["2026-01-02", "2026-01-03", "2026-01-04"] }
```

---

### GET /data/airports — ✅

Header: `Authorization: Bearer <token>`

Referencia estática (sin load en tiempo real). Para carga en vivo usar `/simulations/:id/airports`.

Response `200`:
```json
[ { "icao": "SKBO", "city": "Bogota", "continent": "SOUTH_AMERICA", "lat": 4.701, "lon": -74.147, "capacity": 50 } ]
```

---

### GET /data/routes — ✅

Response `200`:
```json
[ { "id": "SKBO-SEQM-19:00", "originIcao": "SKBO", "destIcao": "SEQM", "capacity": 120, "depTimeLocal": "19:00", "arrTimeLocal": "20:00" } ]
```

---

## 6. Simulación — Ciclo de vida

### POST /simulations — ✅

Todos los campos son obligatorios. No hay valores por defecto.

**Modo DB** — simula con datos históricos precargados:
```json
{
  "dataSource":       "DB",
  "solverTimingMode": "REAL_TIME",
  "optimizerMode":    "ALNS_ONLY",
  "simStart":         "2026-01-02T00:00:00Z",
  "simEnd":           "2026-01-07T00:00:00Z",
  "speedFactor":      480.0
}
```

**Modo MANUAL** — solo acepta envíos por API, sin datos precargados, sin inicio/fin. 🔴 No implementado aún.
```json
{
  "dataSource":       "MANUAL",
  "solverTimingMode": "REAL_TIME",
  "optimizerMode":    "ALNS_ONLY",
  "simStart":         null,
  "simEnd":           null,
  "speedFactor":      null
}
```

| Campo | Valores válidos | Requerido |
|-------|----------------|-----------|
| `dataSource` | `DB` \| `MANUAL` | siempre |
| `solverTimingMode` | `REAL_TIME` \| `PAUSE` \| `EVENT_DRIVEN` | siempre |
| `optimizerMode` | `ALNS_ONLY` \| `GENETIC_ONLY` \| `ALNS_ACTIVE_GENETIC_EVAL` \| `GENETIC_ACTIVE_ALNS_EVAL` | siempre |
| `simStart` | ISO-8601 UTC | solo DB |
| `simEnd` | ISO-8601 UTC | solo DB |
| `speedFactor` | número > 0 | solo DB |

> Si se solicita una combinación no implementada, el servidor devuelve `501 Not Implemented` con el mensaje de error.  
> Implementado: `DB + REAL_TIME + ALNS_ONLY`.

Response `201`:
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "starting",
  "simTime": "2026-01-02T00:00:00Z",
  "simStart": "2026-01-02T00:00:00Z",
  "simEnd": "2026-01-07T00:00:00Z"
}
```

`status`: `starting` | `running` | `paused` | `completed` | `stopped`  
Errores: `400` campo faltante o inválido · `409` el usuario ya tiene una sesión activa · `501` combinación no implementada

---

### GET /simulations/mine — ✅

Devuelve la sesión activa del usuario autenticado. Útil para reconectar desde otro navegador sin conocer el `id`.

Response `200`: mismo schema que el POST.  
Errores: `404` el usuario no tiene ninguna sesión activa

---

### GET /simulations/:id — ✅

Response `200`: mismo schema que el POST.  
Errores: `404` sesión no existe

---

### POST /simulations/:id/pause — ✅

Response `204`. Errores: `409` no está en `running`

---

### POST /simulations/:id/resume — ✅

Response `204`. Errores: `409` no está en `paused`

---

### POST /simulations/:id/stop — ✅

Detiene hilos y libera recursos. No reversible.

Response `204`

---

### POST /simulations/:id/disruptions — ⏳

`kind`: `CANCELLATION` | `AVERIA` | `SEGMENT_BLOCK` | `NODE_BLOCK`

Body (`application/json`):
```json
{
  "kind":       "CANCELLATION",
  "flightId":   "SKBO-SEQM-19:00-20260103",
  "originIcao": null,
  "destIcao":   null,
  "fromUtc":    "2026-01-03T19:00:00Z",
  "toUtc":      "2026-01-03T20:00:00Z",
  "severity":   5
}
```

Response `200`:
```json
{ "affectedFlights": 1, "flightIds": ["SKBO-SEQM-19:00-20260103"] }
```

---

## 7. Monitoreo

### GET /simulations/:id/dashboard — ✅

Response `200`:
```json
{
  "simTime":           "2026-01-03T14:22:00Z",
  "delivered":         1240,
  "pending":           87,
  "assigned":          312,
  "inFlight":          95,
  "slaBreaches":       14,
  "throughputPerHour": 38.5
}
```

---

### GET /simulations/:id/snapshot — ✅

Estado completo: vuelos + maletas + aeropuertos del horizonte actual.

Response `200`:
```json
{
  "simTime": "2026-01-03T14:22:00Z",
  "simStart": "2026-01-02T00:00:00Z",
  "simEnd": "2026-01-07T00:00:00Z",
  "status": "running",
  "flights": [
    { "flightId": "SKBO-SEQM-19:00-20260103", "fromIcao": "SKBO", "toIcao": "SEQM",
      "depTime": "2026-01-03T00:00:00Z", "arrTime": "2026-01-03T01:00:00Z",
      "status": "IN_FLIGHT", "load": 45, "capacity": 120 }
  ],
  "baggages": [
    { "baggageId": "S1-B3", "status": "IN_FLIGHT", "currentIcao": "SKBO",
      "flightId": "SKBO-SEQM-19:00-20260103", "destIcao": "SEQM", "deadlineUtc": "2026-01-03T22:00:00Z" }
  ],
  "airports": [
    { "icao": "SKBO", "city": "Bogota", "continent": "SOUTH_AMERICA",
      "load": 45, "pending": 12, "capacity": 50 }
  ]
}
```

---

## 8. Vuelos (panel) 🔴

> **No implementado.** Roadmap Fase 2 — E02, E03, E04, E05.

`occupancyLevel` se calcula como: `EMPTY` = 0 %, `GREEN` ≤ 60 %, `AMBER` ≤ 85 %, `RED` > 85 %.  
`status` de vuelo: `SCHEDULED` | `IN_FLIGHT` | `LANDED` | `CANCELLED`

---

### GET /simulations/:id/flights — 🔴

Lista todos los vuelos del horizonte con ocupación y semáforo.

Response `200`:
```json
[
  {
    "flightId":      "SKBO-SEQM-19:00-20260103",
    "fromIcao":      "SKBO",
    "toIcao":        "SEQM",
    "depTime":       "2026-01-03T19:00:00Z",
    "arrTime":       "2026-01-03T20:00:00Z",
    "status":        "IN_FLIGHT",
    "load":          45,
    "capacity":      120,
    "occupancyPct":  37.5,
    "occupancyLevel": "GREEN"
  }
]
```

---

### GET /simulations/:id/flights/:flightId — 🔴

Detalle de un vuelo: igual que el anterior + envíos y maletas a bordo.

Response `200`:
```json
{
  "flightId":      "SKBO-SEQM-19:00-20260103",
  "fromIcao":      "SKBO",
  "toIcao":        "SEQM",
  "depTime":       "2026-01-03T19:00:00Z",
  "arrTime":       "2026-01-03T20:00:00Z",
  "status":        "IN_FLIGHT",
  "load":          45,
  "capacity":      120,
  "occupancyPct":  37.5,
  "occupancyLevel": "GREEN",
  "shipments": [
    {
      "shipmentId":  "S1",
      "originIcao":  "SPJC",
      "destIcao":    "SEQM",
      "baggageCount": 3,
      "baggages": [
        { "baggageId": "S1-B1", "destIcao": "SEQM", "deadlineUtc": "2026-01-03T22:00:00Z" },
        { "baggageId": "S1-B2", "destIcao": "SEQM", "deadlineUtc": "2026-01-03T22:00:00Z" },
        { "baggageId": "S1-B3", "destIcao": "SEQM", "deadlineUtc": "2026-01-03T22:00:00Z" }
      ]
    }
  ]
}
```

Errores: `404` vuelo no existe en el horizonte de la sesión

---

## 9. Almacenes (panel) 🔴

> **No implementado.** Roadmap Fase 3 — E17, E18, E20, E21, E23.

`occupancyLevel` del almacén: `EMPTY` = 0 maletas, `GREEN` ≤ 60 %, `AMBER` ≤ 85 %, `RED` > 85 % de capacidad.

---

### GET /simulations/:id/airports — 🔴

Lista todos los aeropuertos con carga en tiempo real. Distinto de `GET /data/airports` (estático): este refleja el estado vivo del grafo.

Response `200`:
```json
[
  {
    "icao":          "SKBO",
    "city":          "Bogota",
    "continent":     "SOUTH_AMERICA",
    "load":          45,
    "capacity":      50,
    "pending":       12,
    "occupancyPct":  90.0,
    "occupancyLevel": "RED"
  }
]
```

---

### GET /simulations/:id/airports/:icao/inbound — 🔴

Vuelos planificados que llegarán a este aeropuerto, con sus maletas asignadas.  
Fuente: baggages en `assignedBaggages` cuya ruta incluye un `FlightEdge` con `toIcao == icao`.

Response `200`:
```json
{
  "icao":    "SKBO",
  "simTime": "2026-01-03T14:22:00Z",
  "inbound": [
    {
      "flightId":    "SPJC-SKBO-17:00-20260103",
      "fromIcao":    "SPJC",
      "arrTime":     "2026-01-03T19:00:00Z",
      "baggageCount": 8,
      "shipmentIds": ["S3", "S7"]
    }
  ]
}
```

Errores: `404` aeropuerto no existe en la sesión

---

### GET /simulations/:id/airports/:icao/outbound — 🔴

Vuelos planificados que saldrán de este aeropuerto, con sus maletas asignadas.  
Fuente: baggages en `assignedBaggages` cuya próxima `FlightEdge` tiene `fromIcao == icao`.

Response `200`:
```json
{
  "icao":    "SKBO",
  "simTime": "2026-01-03T14:22:00Z",
  "outbound": [
    {
      "flightId":    "SKBO-SEQM-19:00-20260103",
      "toIcao":      "SEQM",
      "depTime":     "2026-01-03T19:00:00Z",
      "baggageCount": 45,
      "shipmentIds": ["S1", "S2", "S4"]
    }
  ]
}
```

---

### GET /simulations/:id/airports/:icao/transit — 🔴

Maletas actualmente esperando conexión en este aeropuerto (currentEdge = WaitEdge en ese nodo).

Response `200`:
```json
{
  "icao":    "SKBO",
  "simTime": "2026-01-03T14:22:00Z",
  "transit": [
    {
      "baggageId":   "S3-B1",
      "shipmentId":  "S3",
      "destIcao":    "SEQM",
      "deadlineUtc": "2026-01-03T22:00:00Z",
      "nextFlightId": "SKBO-SEQM-19:00-20260103",
      "nextDepTime": "2026-01-03T19:00:00Z"
    }
  ]
}
```

`nextFlightId` es `null` si el baggage está `PENDING` (sin ruta asignada todavía).

---

## 10. Envíos (panel) 🔴

> **No implementado.** Roadmap Fase 4 — E30, E31, E32.

Los envíos se reconstruyen agrupando maletas por `shipmentId` (prefijo del `baggageId`: `"S1-B3"` → `"S1"`).  
Requiere que `SimulationSession` mantenga un `Map<String, Shipment>` activo.

---

### GET /simulations/:id/shipments/planned — 🔴

Envíos con ruta asignada, esperando en un aeropuerto (baggages en estado `WAITING`).

Response `200`:
```json
[
  {
    "shipmentId":  "S1",
    "originIcao":  "SPJC",
    "destIcao":    "SEQM",
    "currentIcao": "SKBO",
    "baggageCount": 3,
    "nextFlightId": "SKBO-SEQM-19:00-20260103",
    "nextDepTime": "2026-01-03T19:00:00Z",
    "deadlineUtc": "2026-01-03T22:00:00Z"
  }
]
```

---

### GET /simulations/:id/shipments/in-flight — 🔴

Envíos actualmente en el aire (al menos una maleta en estado `IN_FLIGHT`).

Response `200`:
```json
[
  {
    "shipmentId":     "S1",
    "originIcao":     "SPJC",
    "destIcao":       "SEQM",
    "baggageCount":   3,
    "currentFlightId": "SKBO-SEQM-19:00-20260103",
    "fromIcao":       "SKBO",
    "toIcao":         "SEQM",
    "arrTime":        "2026-01-03T20:00:00Z",
    "deadlineUtc":    "2026-01-03T22:00:00Z"
  }
]
```

---

### GET /simulations/:id/shipments/delivered?hours=N — 🔴

Envíos entregados en las últimas `N` horas de tiempo **simulado** (default `hours=4`).  
Requiere campo `deliveredAt: Instant` en `Baggage`.

Response `200`:
```json
[
  {
    "shipmentId":  "S2",
    "originIcao":  "SPJC",
    "destIcao":    "SEQM",
    "baggageCount": 2,
    "deliveredAt": "2026-01-03T12:30:00Z",
    "deadlineUtc": "2026-01-03T14:00:00Z",
    "onTime":      true
  }
]
```

`onTime` = `deliveredAt <= deadlineUtc`

---

## 11. Tracking

### GET /simulations/:id/baggage/:baggageId — ⏳

Response `200`:
```json
{
  "baggageId":   "S1-B3",
  "status":      "IN_FLIGHT",
  "currentIcao": "SKBO",
  "flightId":    "SKBO-SEQM-19:00-20260103",
  "destIcao":    "SEQM",
  "deadlineUtc": "2026-01-03T22:00:00Z"
}
```

`status`: `PENDING` | `WAITING` | `IN_FLIGHT` | `DELIVERED`  
`flightId` es `null` salvo cuando `status = IN_FLIGHT`

Errores: `404` maleta no existe en la sesión

---

### GET /simulations/:id/baggage/:baggageId/route — ⏳

Escalas recorridas + en curso + planificadas.

Response `200`:
```json
{
  "baggageId":   "S1-B3",
  "status":      "IN_FLIGHT",
  "currentIcao": "SKBO",
  "destIcao":    "SEQM",
  "deadlineUtc": "2026-01-03T22:00:00Z",
  "legs": [
    { "fromIcao": "SPJC", "toIcao": "SKBO",
      "depTime": "2026-01-02T10:00:00Z", "arrTime": "2026-01-02T12:00:00Z",
      "flightId": "SPJC-SKBO-10:00-20260102", "state": "TRAVELED" },
    { "fromIcao": "SKBO", "toIcao": "SEQM",
      "depTime": "2026-01-03T19:00:00Z", "arrTime": "2026-01-03T20:00:00Z",
      "flightId": "SKBO-SEQM-19:00-20260103", "state": "IN_FLIGHT" }
  ]
}
```

`state`: `TRAVELED` | `IN_FLIGHT` | `PLANNED`

---

## 12. Reportes 🔴

> **No implementado.** Roadmap Fase 8 — G08, G09, G10.

### GET /simulations/:id/reports/summary — 🔴

Disponible en cualquier momento; más completo cuando `status = completed`.

Response `200`:
```json
{
  "simStart":          "2026-01-02T00:00:00Z",
  "simEnd":            "2026-01-07T00:00:00Z",
  "status":            "completed",
  "totalShipments":    450,
  "totalBaggages":     1250,
  "delivered":         1180,
  "slaBreaches":       34,
  "pending":           36,
  "inFlight":          0,
  "avgDeliveryHours":  18.4,
  "throughputPerHour": 38.5,
  "topRoutes": [
    { "fromIcao": "SKBO", "toIcao": "SEQM", "flightCount": 145 }
  ]
}
```

---

## 13. WebSocket

`ws://localhost:8080/api/v1/simulations/:id/ws`  
Header: `Authorization: Bearer <token>`

Solo recibe — el cliente no envía. Push desde `InMemoryStatePublisher`.

```json
{ "seq": 42, "type": "BAGGAGE_DEPARTED", "simTime": "2026-01-03T10:00:00Z", "payload": {} }
```

`seq` es un entero incremental por sesión (arranca en 0). El front detecta gaps comparando el `seq` recibido con el último conocido y llama a `/snapshot` para re-sincronizar.

| type | payload |
|------|---------|
| `FLIGHT_SCHEDULED` | `{ flightId, fromIcao, toIcao, depTime, capacity }` |
| `FLIGHT_DEPARTED` | `{ flightId, fromIcao, toIcao, load, capacity }` |
| `FLIGHT_ARRIVED` | `{ flightId, toIcao, load }` |
| `FLIGHT_CANCELLED` | `{ flightId }` |
| `BAGGAGE_DEPARTED` | `{ baggageId, flightId, fromIcao, toIcao }` |
| `BAGGAGE_ARRIVED` | `{ baggageId, flightId, currentIcao }` |
| `BAGGAGE_DELIVERED` | `{ baggageId, currentIcao }` |
| `BAGGAGE_PENDING` | `{ baggageId, currentIcao }` |
| `BAGGAGE_ASSIGNED` | `{ baggageId, route: [flightId, ...] }` |
| `SHIPMENT_CREATED` | `{ shipmentId, baggageIds: [...], originIcao, destIcao, deadlineUtc }` |
| `SIM_STATUS` | `{ status }` |

Reconexión: el servidor no tiene replay — los eventos perdidos no se recuperan. Usar `GET /simulations/:id/snapshot` para re-sincronizar el estado completo tras reconectar.
