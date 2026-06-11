# API Testing — TASF Simulator

Base URL: `http://localhost:8080/api/v1`

**Estado:**
- ✅ Funciona
- ❌ Falla
- ⏳ Sin probar

---

## Resumen de estado

| Estado | Método | Endpoint | Notas |
|--------|--------|----------|-------|
| ⏳ | POST | `/auth/login` | |
| ⏳ | POST | `/auth/refresh` | |
| ⏳ | POST | `/admin/airports?mode=replace` | archivo UTF-16 |
| ⏳ | POST | `/admin/airports?mode=merge` | |
| ⏳ | POST | `/admin/flights?mode=replace` | archivo ISO-8859-1, requiere aeropuertos |
| ⏳ | POST | `/admin/flights?mode=merge` | |
| ⏳ | POST | `/admin/shipments` | varios archivos, falla 409 si ya hay datos |
| ⏳ | POST | `/admin/shipments/file` | un solo archivo |
| ⏳ | DELETE | `/admin/shipments` | borra todo el histórico |
| ⏳ | GET | `/admin/historical` | lista aeropuertos con datos cargados |
| ⏳ | GET | `/admin/historical/:icao` | detalle por aeropuerto |
| ⏳ | POST | `/admin/historical/:icao?mode=merge` | sube `_envios_ICAO_.txt` |
| ⏳ | DELETE | `/admin/historical/:icao` | borra histórico de un aeropuerto |
| ⏳ | DELETE | `/admin/historical` | borra todo el histórico |
| ⏳ | GET | `/data/available-days` | |
| ⏳ | GET | `/data/airports` | |
| ⏳ | GET | `/data/routes` | |
| ⏳ | POST | `/simulations` | crea e inicia sesión |
| ⏳ | GET | `/simulations/:id` | estado de la sesión |
| ⏳ | POST | `/simulations/:id/pause` | 409 si no está running |
| ⏳ | POST | `/simulations/:id/resume` | 409 si no está paused |
| ⏳ | POST | `/simulations/:id/stop` | libera recursos |
| ⏳ | POST | `/simulations/:id/disruptions` | inyecta cancelación/avería |
| ⏳ | GET | `/simulations/:id/dashboard` | métricas en tiempo real |
| ⏳ | GET | `/simulations/:id/snapshot` | estado completo |
| ⏳ | GET | `/simulations/:id/baggage/:baggageId` | tracking de una maleta |
| ⏳ | GET | `/simulations/:id/baggage/:baggageId/route` | ruta completa |

---

## Auth

### POST /auth/login — ⏳

Header: ninguno  
Body (`application/json`):
```json
{ "username": "admin", "password": "****" }
```

Response `200`:
```json
{ "accessToken": "<jwt>", "expiresAt": "2026-01-02T01:00:00Z" }
```

Errores: `401` credenciales inválidas · `429` más de 5 intentos/minuto por IP

---

### POST /auth/refresh — ⏳

Header: `Authorization: Bearer <token>`  
Body: ninguno

Response `200`:
```json
{ "accessToken": "<jwt>", "expiresAt": "2026-01-02T02:00:00Z" }
```

> Todos los endpoints siguientes requieren `Authorization: Bearer <token>`.

---

## Admin — Datos de referencia

### POST /admin/airports?mode=replace|merge — ⏳

Request: `multipart/form-data` → campo `file` (archivo **UTF-16**)

- `replace` — borra todo y recarga (en cascada borra vuelos también)
- `merge` — inserta nuevos, actualiza existentes por ICAO

Response `200`:
```json
{
  "message": "Aeropuertos actualizados (merge)",
  "count": 30,
  "errors": [],
  "warnings": []
}
```

---

### POST /admin/flights?mode=replace|merge — ⏳

Request: `multipart/form-data` → campo `file` (archivo **ISO-8859-1**)  
**Requiere aeropuertos cargados.**

Response `200`:
```json
{
  "message": "Vuelos actualizados (merge)",
  "count": 2866,
  "errors": [],
  "warnings": []
}
```

---

## Admin — Shipments (carga bulk)

### POST /admin/shipments — ⏳

Sube **varios archivos** `_envios_ICAO_.txt` a la vez.

Request: `multipart/form-data` → campo `files` (múltiples archivos)

Response `201`:
```json
{
  "message": "Shipments cargados y ordenados por entry_utc",
  "count": 12450,
  "errors": [],
  "warnings": []
}
```

Errores: `409` si ya hay shipments en BD (hacer `DELETE /admin/shipments` primero)

---

### POST /admin/shipments/file — ⏳

Sube **un solo archivo** `_envios_ICAO_.txt`.

Request: `multipart/form-data` → campo `file`

Response `201`:
```json
{
  "message": "Shipments cargados desde _envios_SKBO_.txt",
  "count": 1523,
  "errors": [],
  "warnings": []
}
```

---

### DELETE /admin/shipments — ⏳

Borra **todos** los shipments de la BD.

Response `200`:
```json
{ "message": "Shipments eliminados", "count": 12450, "errors": [], "warnings": [] }
```

---

## Admin — Histórico por aeropuerto

### GET /admin/historical — ⏳

Response `200`:
```json
[
  { "icao": "SKBO", "count": 1523 },
  { "icao": "SEQM", "count": 874 }
]
```

---

### GET /admin/historical/:icao — ⏳

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

Errores: `404` aeropuerto no registrado en BD

---

### POST /admin/historical/:icao?mode=merge|replace — ⏳

Request: `multipart/form-data` → campo `file` (el nombre debe contener el ICAO)

Response `201`:
```json
{
  "message": "Histórico de SKBO actualizado (merge)",
  "count": 1523,
  "errors": [],
  "warnings": []
}
```

Errores: `400` si el nombre del archivo no coincide con el ICAO del path

---

### DELETE /admin/historical/:icao — ⏳

Response `200`:
```json
{ "message": "Histórico de SKBO eliminado", "count": 1523, "errors": [], "warnings": [] }
```

Errores: `404` aeropuerto no registrado en BD

---

### DELETE /admin/historical — ⏳

Response `200`:
```json
{ "message": "Todo el histórico eliminado", "count": 12450, "errors": [], "warnings": [] }
```

---

## Datos disponibles

### GET /data/available-days — ⏳

Response `200`:
```json
{ "availableDates": ["2026-01-02", "2026-01-03", "2026-01-04"] }
```

---

### GET /data/airports — ⏳

Response `200`:
```json
[
  { "icao": "SKBO", "city": "Bogota", "continent": "SOUTH_AMERICA",
    "lat": 4.701, "lon": -74.147, "capacity": 50 }
]
```

---

### GET /data/routes — ⏳

Response `200`:
```json
[
  { "id": "SKBO-SEQM-19:00", "originIcao": "SKBO", "destIcao": "SEQM",
    "capacity": 120, "depTimeLocal": "19:00", "arrTimeLocal": "20:00" }
]
```

---

## Simulación

### POST /simulations — ⏳

Body (`application/json`):
```json
{
  "simStart": "2026-01-02T00:00:00Z",
  "simEnd":   "2026-01-07T00:00:00Z"
}
```

Response `201`:
```json
{
  "id":       "550e8400-e29b-41d4-a716-446655440000",
  "status":   "starting",
  "simTime":  "2026-01-02T00:00:00Z",
  "simStart": "2026-01-02T00:00:00Z",
  "simEnd":   "2026-01-07T00:00:00Z"
}
```

`status`: `starting` | `running` | `paused` | `completed` | `stopped`

---

### GET /simulations/:id — ⏳

Response `200`: mismo schema que POST.  
Errores: `404` sesión no existe

---

### POST /simulations/:id/pause — ⏳

Response `204` (sin body).  
Errores: `409` sesión no está en `running`

---

### POST /simulations/:id/resume — ⏳

Response `204`.  
Errores: `409` sesión no está en `paused`

---

### POST /simulations/:id/stop — ⏳

Detiene hilos y libera recursos. No reversible.

Response `204`.

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

## Monitoreo

### GET /simulations/:id/dashboard — ⏳

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

### GET /simulations/:id/snapshot — ⏳

Estado completo: vuelos activos + maletas + aeropuertos.

Response `200`:
```json
{
  "simTime":  "2026-01-03T14:22:00Z",
  "simStart": "2026-01-02T00:00:00Z",
  "simEnd":   "2026-01-07T00:00:00Z",
  "status":   "running",
  "flights": [
    { "flightId": "SKBO-SEQM-19:00-20260103", "fromIcao": "SKBO", "toIcao": "SEQM",
      "depTime": "2026-01-03T00:00:00Z", "arrTime": "2026-01-03T01:00:00Z",
      "status": "IN_FLIGHT", "load": 45, "capacity": 120 }
  ],
  "baggages": [
    { "baggageId": "S1-B3", "status": "IN_FLIGHT", "currentIcao": "SKBO",
      "flightId": "SKBO-SEQM-19:00-20260103", "destIcao": "SEQM",
      "deadlineUtc": "2026-01-03T22:00:00Z" }
  ],
  "airports": [
    { "icao": "SKBO", "city": "Bogota", "continent": "SOUTH_AMERICA",
      "load": 45, "pending": 12, "capacity": 50 }
  ]
}
```

---

## Tracking

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

`state` por tramo: `TRAVELED` | `IN_FLIGHT` | `PLANNED`

Errores: `404` maleta no existe en la sesión

---

## WebSocket

`ws://localhost:8080/api/v1/simulations/:id/ws`  
Header: `Authorization: Bearer <token>`

Solo recibe — el cliente no envía. Push directo desde Redis Stream.

```json
{ "seq": "1748123456789-0", "type": "BAGGAGE_DEPARTED",
  "simTime": "2026-01-03T10:00:00Z", "payload": {} }
```

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

Reconexión: enviar el último `seq` recibido; el servidor reenvía los eventos perdidos antes de continuar en modo push.
