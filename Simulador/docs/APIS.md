# Contrato API — TASF Cargo Routing Simulator

Versión: 1.1  
Base URL: `http://host:8080/api/v1`  
WebSocket: `ws://host:8080/api/v1/simulations/:id/ws`

> **Estado de pruebas (Postman):**
> - ✅ `POST /admin/airports` — probado y funcionando
> - ✅ `POST /admin/flights` — probado y funcionando
> - ⏳ `POST /admin/historical/{icao}` — implementado, pendiente de prueba
> - ⏳ Resto de endpoints — implementados, pendientes de prueba

---

## Auth

Usuarios almacenados en `public.users` (PostgreSQL). Contraseñas hasheadas con BCrypt. Un solo usuario activo en esta entrega.

```
POST /api/v1/auth/login
POST /api/v1/auth/refresh
```

### POST /auth/login

Request:
```json
{ "username": "admin", "password": "****" }
```

Response `200`:
```json
{
  "accessToken": "<jwt>",
  "expiresAt": "2026-01-02T01:00:00Z"
}
```

Response `401`: credenciales inválidas.

### POST /auth/refresh

Request header: `Authorization: Bearer <accessToken>`

Response `200`:
```json
{
  "accessToken": "<jwt>",
  "expiresAt": "2026-01-02T02:00:00Z"
}
```

---

## Administración — Datos de referencia

> Auth requerida: `Authorization: Bearer <token>` en todos.

### POST /admin/airports?mode=merge|replace ✅

Sube el archivo TXT de aeropuertos (UTF-16).

- `merge` — inserta nuevos y actualiza los existentes por ICAO
- `replace` — borra todos los aeropuertos (y en cascada los vuelos) y recarga

Request: `multipart/form-data` → field `file`

Response `200`:
```json
{
  "message": "Aeropuertos actualizados (merge)",
  "count": 30,
  "errors": [],
  "warnings": []
}
```

`warnings` incluye aviso si `replace` también borró vuelos.

### POST /admin/flights?mode=merge|replace ✅

Sube el archivo TXT de vuelos programados (ISO-8859-1).  
Requiere aeropuertos cargados primero.

Request: `multipart/form-data` → field `file`

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

## Administración — Históricos de shipments

> Auth requerida: `Authorization: Bearer <token>` en todos.  
> Un archivo por aeropuerto origen, formato `_envios_ICAO_.txt`.  
> Límite por archivo: 25MB.

### GET /admin/historical ⏳

Lista qué aeropuertos tienen shipments históricos cargados.

Response `200`:
```json
[
  { "icao": "SKBO", "count": 1523 },
  { "icao": "SEQM", "count": 874 }
]
```

### GET /admin/historical/:icao ⏳

Detalle de un aeropuerto: total y conteo por día UTC.

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

Response `404`: aeropuerto no registrado en BD.

### POST /admin/historical/:icao?mode=merge|replace ⏳

Sube `_envios_ICAO_.txt` para el aeropuerto indicado.

- `merge` — agrega o actualiza por ID de shipment
- `replace` — borra el histórico de ese ICAO y recarga

Validaciones:
- El nombre del archivo debe contener el ICAO del path
- El aeropuerto origen debe existir en BD
- Los aeropuertos destino deben existir en BD (los inválidos van a `errors`, no abortan)
- Origen == destino se rechaza por línea y va a `errors`

Request: `multipart/form-data` → field `file`

Response `201`:
```json
{
  "message": "Histórico de SKBO actualizado (merge)",
  "count": 1523,
  "errors": [
    "Aeropuerto destino no registrado: XXXX (línea: ...)",
    "Origen igual a destino ignorado: ..."
  ],
  "warnings": []
}
```

### DELETE /admin/historical/:icao ⏳

Borra todos los shipments históricos del aeropuerto indicado.

Response `200`:
```json
{ "message": "Histórico de SKBO eliminado", "count": 1523, "errors": [], "warnings": [] }
```

### DELETE /admin/historical ⏳

Borra todo el histórico de todos los aeropuertos.

Response `200`:
```json
{ "message": "Todo el histórico eliminado", "count": 12450, "errors": [], "warnings": [] }
```

---

## Datos disponibles

> Auth requerida: `Authorization: Bearer <token>` en todos.

### GET /data/available-days

Devuelve las fechas que tienen shipments históricos cargados en BD.

Response `200`:
```json
{
  "availableDates": ["2026-01-02", "2026-01-03", "2026-01-04"]
}
```

### GET /data/airports

Response `200`: lista de aeropuertos con ICAO, ciudad, continente, capacidad, lat/lon.

### GET /data/routes

Response `200`: lista de vuelos programados con origen, destino, horarios locales y capacidad.

---

## Simulación

> Auth requerida: `Authorization: Bearer <token>` en todos.

Pueden correr dos sesiones simultáneas, cada una con su propio grafo e hilos.

### POST /simulations

Crea e inicia una sesión de simulación. El frontend envía el rango de fechas; el backend calcula el `speedFactor` internamente.

Request:
```json
{
  "simStart": "2026-01-02T00:00:00Z",
  "simEnd":   "2026-01-07T00:00:00Z"
}
```

Response `201`:
```json
{
  "id":      "550e8400-e29b-41d4-a716-446655440000",
  "status":  "starting",
  "simStart": "2026-01-02T00:00:00Z",
  "simEnd":   "2026-01-07T00:00:00Z"
}
```

`status` posibles: `starting` | `running` | `paused` | `completed` | `stopped`

### GET /simulations/:id

Response `200`:
```json
{
  "id":      "550e8400-...",
  "status":  "running",
  "simTime": "2026-01-03T14:22:00Z",
  "simStart": "2026-01-02T00:00:00Z",
  "simEnd":   "2026-01-07T00:00:00Z"
}
```

Response `404`: sesión no existe.

### POST /simulations/:id/pause

Response `204` (sin body).  
Response `409`: sesión no está en estado `running`.

### POST /simulations/:id/resume

Response `204`.  
Response `409`: sesión no está en estado `paused`.

### POST /simulations/:id/stop

Response `204`. Detiene todos los hilos y libera recursos.

---

## Monitoreo

> Auth requerida: `Authorization: Bearer <token>` en todos.

### GET /simulations/:id/dashboard

Calculado en tiempo real desde el grafo en memoria.

Response `200`:
```json
{
  "simTime":          "2026-01-03T14:22:00Z",
  "delivered":        1240,
  "pending":          87,
  "assigned":         312,
  "inFlight":         95,
  "slaBreaches":      14,
  "throughputPerHour": 38.5
}
```

Campos:
- `delivered` — baggages entregados en destino
- `pending` — baggages sin ruta asignada (esperando solución ALNS)
- `assigned` — baggages con ruta planificada, esperando vuelo
- `inFlight` — baggages actualmente en el aire
- `slaBreaches` — baggages que superaron su `deadlineUtc`
- `throughputPerHour` — `delivered / horasSimuladasTranscurridas`

---

## Tracking

> Auth requerida: `Authorization: Bearer <token>` en todos.

### GET /simulations/:id/baggage/:baggageId

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

`status` posibles: `WAITING` | `IN_FLIGHT` | `PENDING` | `DELIVERED`

- `flightId` — presente solo si `status = IN_FLIGHT`; null en los demás casos
- `currentIcao` — aeropuerto donde está ahora (si `IN_FLIGHT`: aeropuerto de salida del tramo en curso)

Response `404`: baggage no existe en la sesión.

---

## WebSocket

Conexión: `ws://host:8080/api/v1/simulations/:id/ws`

Requiere header `Authorization: Bearer <accessToken>`.

El servidor hace push de eventos en cuanto el `SimulationRunner` los produce (vía Redis Stream). El cliente no envía mensajes — solo recibe.

### Estructura de todos los eventos

```json
{
  "seq":     "1748123456789-0",
  "type":    "BAGGAGE_DEPARTED",
  "simTime": "2026-01-03T10:00:00Z",
  "payload": {}
}
```

- `seq` — ID del Redis Stream (monotónico). Usar para detectar mensajes perdidos y deduplicar reconexiones.
- `type` — uno de los tipos listados abajo
- `simTime` — tiempo de simulación en que ocurrió el evento

### Tipos de evento y payload

| type | payload |
|---|---|
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
| `SIM_STATUS` | `{ status }` — emitido en pause/resume/completed/stopped |

### Reconexión

Al reconectarse, el cliente envía el último `seq` recibido. El servidor hace `XREAD COUNT 1000 STREAMS sim:events:{id} <lastSeq>` y reenvía los eventos perdidos antes de entrar en modo push.

---

## Reglas de consistencia

- Todos los timestamps son UTC en formato ISO-8601 (`2026-01-02T13:00:00Z`)
- Los IDs de vuelo siguen el formato `ORIG-DEST-HH:mm-yyyyMMdd` (ej: `SKBO-SEQM-19:00-20260102`)
- Los IDs de baggage siguen el formato `<shipmentId>-B<n>` (ej: `S1-B3`)
- El backend es fuente de verdad de toda lógica: rutas, ALNS, deadlines, estado operacional
- El frontend no calcula nada — solo renderiza lo que recibe
