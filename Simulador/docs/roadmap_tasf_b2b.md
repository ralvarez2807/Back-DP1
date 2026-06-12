# Roadmap TASF B2B — Equipo 6F

> Basado en la evaluación del profesor (91.EvaSw). Estado registrado en el avance del Estado 1.

## Resumen del estado actual

| Estado | Cantidad | % del total |
|---|---|---|
| ✅ Conforme | 42 | 33% |
| ⚠️ Parcial | 18 | 14% |
| ❌ Falta | 51 | 40% |
| 🚫 No entregado | 4 | 3% |
| **Total que aplica** | **127** | |

---

## Leyenda

| Símbolo | Significado |
|---|---|
| ✅ | Conforme — evaluado y aprobado |
| ⚠️ | Parcial — existe pero incompleto o con error |
| ❌ | Falta — no implementado |
| 🚫 | No entregado — explícitamente marcado como ausente |
| `[BACK]` | Trabajo en el backend (Java) |
| `[FRONT]` | Trabajo en el frontend (React + D3.js) |
| `[AMBOS]` | Requiere cambios en back y front |
| `[DOC]` | Documentación / diagrama |

---

## Fase 0 — Despliegue (BLOQUEANTE)

> Sin esto el profesor no puede evaluar nada en vivo. Va antes que todo.

| Ítem | Estado | Tarea |
|---|---|---|
| A20 | 🚫 | `[FRONT]` Resolver errores de seguridad y desplegar componente front en servidor V |
| A21 | 🚫 | `[BACK]` Resolver errores de seguridad y desplegar componente back en servidor V |

---

## Fase 1 — Bugs en el mapa (fixes rápidos)

> Ya existe el mapa y funciona bien en general. Estos son errores puntuales.

### Reloj simulado — minutos congelados

| Ítem | Estado | Descripción | Tarea |
|---|---|---|---|
| C02 | ⚠️ | Inicio pide fecha pero toma la hora localmente | `[FRONT]` Agregar input de hora con nivel de minuto al iniciar simulación |
| C12 | ⚠️ | El minuto simulado no avanza, queda fijo al minuto real de inicio | `[FRONT]` Desacoplar el reloj simulado del reloj del sistema; el tiempo simulado debe avanzar independientemente |
| C13 | ⚠️ | El tiempo transcurrido simulado tampoco muestra minutos correctos | `[FRONT]` Calcular tiempo transcurrido simulado usando el reloj simulado, no el real |
| C15 | ⚠️ | Tiempo transcurrido real muestra horas pero no minutos | `[FRONT]` Mostrar `HH:mm` en el contador de tiempo real transcurrido |

### Pantalla y elementos del mapa

| Ítem | Estado | Descripción | Tarea |
|---|---|---|---|
| C01 | ⚠️ | Los datos de envíos se cargan automáticamente, no hay opción manual | `[FRONT]` Agregar sección/pantalla separada para cargar archivos de envíos manualmente |
| C11 | ⚠️ | Dashboard y Simulación están activos desde el inicio (pantalla sucia) | `[FRONT]` Deshabilitar/ocultar secciones hasta que se inicie el escenario correspondiente |
| C34 | ⚠️ | No se puede determinar si la línea del tramo cambia tras ser recorrido | `[FRONT]` Cambiar estilo (ej. punteado, opacidad reducida) en la línea del tramo cuando el avión ya lo recorrió |

### Aviones sin semáforo

| Ítem | Estado | Descripción | Tarea |
|---|---|---|---|
| C27 | ❌ | Los aviones son de un color uniforme, sin indicación de ocupación | `[FRONT]` Aplicar color semáforo (vacío/verde/ámbar/rojo) al ícono del avión según su % de carga |
| C28 | ❌ | Al pasar el mouse sobre un avión no muestra su stock | `[FRONT]` Agregar tooltip en hover del avión con: vuelo, origen, destino, maletas actuales / capacidad |

---

## Fase 2 — Panel de vuelos (UT)

> El panel lateral existe pero solo muestra información estática. Toda la sección de vuelos está en cero. Es el hueco más grande del proyecto.

### Datos y endpoints necesarios

| Ítem | Estado | Descripción | Tarea |
|---|---|---|---|
| E02 | ❌ | No hay lista de vuelos con ocupación | `[BACK]` Endpoint `GET /api/v1/flights/status` que devuelva: ID vuelo, origen, destino, hora salida, hora llegada, maletas actuales, capacidad máxima |
| E05 | ❌ | No hay color semáforo por vuelo | `[BACK]` Incluir en el endpoint anterior el campo `occupancyLevel: EMPTY / GREEN / AMBER / RED` calculado según rangos configurables |

### Lista y visualización

| Ítem | Estado | Descripción | Tarea |
|---|---|---|---|
| E01 | ⚠️ | Paneles solo informativos, no permiten operaciones | `[FRONT]` Rediseñar el panel lateral para que sea colapsable y soporte las secciones de vuelos, almacenes y envíos |
| E02 | ❌ | Sin lista de vuelos | `[FRONT]` Renderizar lista de vuelos activos con: código, ruta (origen → destino), hora, ocupación en número y % |
| E05 | ❌ | Sin semáforo en lista | `[FRONT]` Colorear cada fila de la lista según `occupancyLevel` (vacío/verde/ámbar/rojo) |
| E03 | ❌ | Sin acceso a envíos de un vuelo | `[FRONT]` Al expandir/clicar un vuelo en la lista, mostrar los envíos (grupos de maletas) que transporta |
| E04 | ❌ | Sin acceso a productos (maletas individuales) de un vuelo | `[FRONT]` Al expandir un envío dentro del vuelo, listar las maletas con su ID y estado |

### Búsqueda y filtros

| Ítem | Estado | Descripción | Tarea |
|---|---|---|---|
| E06 | ❌ | Sin búsqueda por código de vuelo | `[FRONT]` Input de búsqueda por código/ID de vuelo |
| E07 | ❌ | Sin búsqueda por aeropuerto de origen | `[FRONT]` Input o selector para filtrar vuelos por ciudad/aeropuerto de origen |
| E08 | ❌ | Sin búsqueda por aeropuerto de destino | `[FRONT]` Input o selector para filtrar vuelos por ciudad/aeropuerto de destino |
| E09 | ❌ | Sin filtro por patrón de código | `[FRONT]` [Opcional] Filtro por patrón (regex o wildcard) en el código del vuelo |
| E10 | ❌ | Sin filtro por origen | `[FRONT]` [Opcional] Dropdown para filtrar vuelos por origen |
| E11 | ❌ | Sin filtro por destino | `[FRONT]` [Opcional] Dropdown para filtrar vuelos por destino |

### Ordenamiento

| Ítem | Estado | Descripción | Tarea |
|---|---|---|---|
| E12 | ❌ | Sin orden por ocupación | `[FRONT]` Botón/header para ordenar la lista por % de ocupación (ascendente/descendente) |
| E13 | ❌ | Sin orden por hora de salida | `[FRONT]` [Opcional] Ordenar lista por hora de salida |
| E14 | ❌ | Sin orden por hora de llegada | `[FRONT]` [Opcional] Ordenar lista por hora de llegada |
| E15 | ❌ | Sin orden por origen | `[FRONT]` [Opcional] Ordenar lista por aeropuerto de origen |
| E16 | ❌ | Sin orden por destino | `[FRONT]` [Opcional] Ordenar lista por aeropuerto de destino |

---

## Fase 3 — Panel de almacenes

> La lista de almacenes existe visualmente en el front pero no está coordinada con el back.

### Coordinación back-front

| Ítem | Estado | Descripción | Tarea |
|---|---|---|---|
| E17 | ⚠️ | Lista de almacenes se muestra en front y se procesa en back, pero no están conectados | `[AMBOS]` Conectar el endpoint de almacenes al componente del panel; sincronizar stock en tiempo real vía WebSocket |

### Datos planificados (endpoints necesarios)

| Ítem | Estado | Descripción | Tarea |
|---|---|---|---|
| E21 | ❌ | Sin info de envíos planificados que entran a cada almacén | `[BACK]` Endpoint `GET /api/v1/airports/{id}/planned-inbound` con lista de envíos que llegarán (según planificación actual) |
| E23 | ❌ | Sin info de envíos planificados que salen de cada almacén | `[BACK]` Endpoint `GET /api/v1/airports/{id}/planned-outbound` con lista de envíos que saldrán |
| E18 | ❌ | Sin acceso a envíos actualmente en el almacén | `[BACK]` Endpoint o campo en el anterior que devuelva envíos en tránsito actualmente en el almacén |

### Visualización en panel

| Ítem | Estado | Descripción | Tarea |
|---|---|---|---|
| E20 | ❌ | Lista de almacenes sin colores semáforo | `[FRONT]` Colorear cada fila de almacén según `occupancyLevel` |
| E19 | ❌ | Sin acceso a productos (maletas) en el almacén | `[FRONT]` [Opcional] Expandir almacén en panel para ver maletas individuales |
| E22 | ❌ | Sin conteo de productos planificados que entran | `[FRONT]` [Opcional] Mostrar cantidad de maletas planificadas entrantes por almacén |
| E24 | ❌ | Sin conteo de productos planificados que salen | `[FRONT]` [Opcional] Mostrar cantidad de maletas planificadas salientes |

### Filtros y ordenamiento

| Ítem | Estado | Descripción | Tarea |
|---|---|---|---|
| E27 | ❌ | Sin orden por ocupación | `[FRONT]` Ordenar lista de almacenes por % de ocupación |
| E25 | ❌ | Sin filtro por código | `[FRONT]` [Opcional] Filtro por código/nombre de aeropuerto |
| E26 | ❌ | Sin filtro por continente | `[FRONT]` [Opcional] Filtro por región continental (América / Asia / Europa) |
| E28 | ❌ | Sin orden por próxima salida | `[FRONT]` [Opcional] Ordenar por hora del próximo vuelo que sale |
| E29 | ❌ | Sin orden por próxima llegada | `[FRONT]` [Opcional] Ordenar por hora del próximo vuelo que llega |

---

## Fase 4 — Panel de envíos

> No existe. Requiere que el back tenga los endpoints correctos.

### Endpoints necesarios

| Ítem | Estado | Descripción | Tarea |
|---|---|---|---|
| E30 | ❌ | Sin lista de envíos planificados | `[BACK]` Endpoint `GET /api/v1/shipments/planned` con destino, vuelo asignado y cantidad de maletas |
| E31 | ❌ | Sin lista de envíos actualmente en vuelo | `[BACK]` Endpoint `GET /api/v1/shipments/in-flight` con origen, destino, vuelo y cantidad |
| E32 | ❌ | Sin lista de envíos entregados en las últimas 4 horas | `[BACK]` Endpoint `GET /api/v1/shipments/delivered?hours=4` |

### Visualización

| Ítem | Estado | Descripción | Tarea |
|---|---|---|---|
| E30 | ❌ | Sin panel de envíos planificados | `[FRONT]` Sección en panel con lista planificada: destino, vuelo, cantidad maletas |
| E31 | ❌ | Sin panel de envíos en vuelo | `[FRONT]` Sección con envíos en tránsito: origen → destino, vuelo, cantidad |
| E32 | ❌ | Sin panel de envíos entregados | `[FRONT]` Sección con entregas recientes (últimas 4h) |

### Filtros

| Ítem | Estado | Descripción | Tarea |
|---|---|---|---|
| E33 | ❌ | Sin filtro por origen en envíos | `[FRONT]` Filtro de envíos por ciudad/aeropuerto de origen |
| E34 | ❌ | Sin filtro por destino en envíos | `[FRONT]` Filtro de envíos por ciudad/aeropuerto de destino |

---

## Fase 5 — Vinculación mapa ↔ panel (bidireccional)

> Depende de que las fases 2, 3 y 4 existan. Los vuelos ya funcionan parcialmente.

### Vuelos — ya funciona

| Ítem | Estado | Descripción |
|---|---|---|
| F07 | ✅ | Seleccionar vuelo en panel → enfocar en mapa |
| F08 | ✅ | Seleccionar vuelo en mapa → enfocar en panel |

### Vuelos — falta completar

| Ítem | Estado | Descripción | Tarea |
|---|---|---|---|
| F16 | ❌ | Sin filtro por semáforo de UT que se refleje en mapa | `[FRONT]` Filtro de color semáforo en panel que oculte/resalte aviones en el mapa según su nivel de carga |
| F18 | ⚠️ | Filtros de UT en panel no se reflejan en el mapa | `[FRONT]` Sincronizar filtros del panel de vuelos con el estado visual del mapa |

### Almacenes

| Ítem | Estado | Descripción | Tarea |
|---|---|---|---|
| F05 | ⚠️ | Seleccionar almacén en panel → enfocar en mapa (parcial) | `[FRONT]` Completar: clic en almacén del panel → centrar mapa y resaltar ese nodo |
| F06 | ⚠️ | Seleccionar almacén en mapa → enfocar en panel (parcial) | `[FRONT]` Completar: clic en nodo del mapa → desplazar panel y resaltar esa fila |
| F15 | ❌ | Sin filtro por semáforo de almacenes en panel | `[FRONT]` Filtro de color semáforo en panel que oculte/resalte aeropuertos en el mapa |
| F17 | ❌ | Sin filtros adicionales de almacenes en panel | `[FRONT]` Filtros de continente u otros que se reflejen en el mapa |

### Envíos / maletas

| Ítem | Estado | Descripción | Tarea |
|---|---|---|---|
| F03 | ⚠️ | Mostrar ruta de envío en mapa (parcial) | `[FRONT]` Al seleccionar un envío, trazar su ruta completa (origen → escalas → destino) en el mapa |
| F01 | ❌ | Sin ruta de maleta individual en mapa | `[FRONT]` Botón/acción para buscar una maleta por ID y mostrar su ruta actual en el mapa |
| F02 | ❌ | Sin ruta histórica de maleta individual | `[FRONT]` Mostrar ruta recorrida hasta el momento con datos de cada escala (hora llegada/salida, estado) |
| F04 | ❌ | Sin rutas históricas de escalas del envío | `[FRONT]` Al seleccionar envío, mostrar escalas anteriores con datos relevantes |
| F09 | ❌ | Sin vinculación envío en panel → mapa | `[FRONT]` Seleccionar un envío en el panel → resaltar su posición/ruta en el mapa |

---

## Fase 6 — Cancelaciones

> No implementadas. Requiere back + front + WebSocket.

| Ítem | Estado | Descripción | Tarea |
|---|---|---|---|
| D14 | ❌ | Las cancelaciones no se presentan en el mapa | `[BACK]` Registrar cancelación de vuelo → replanificar → emitir evento vía WebSocket con vuelo cancelado y maletas afectadas |
| D14 | ❌ | — | `[FRONT]` Recibir evento de cancelación → mostrar indicación visual en el tramo afectado del mapa (ej. línea roja parpadeante o ícono de alerta) |
| D15 | ❌ | La cancelación no está visible el tiempo previsto | `[FRONT]` La indicación de cancelación debe permanecer visible mientras el vuelo estaba planificado; ocultarse cuando el periodo pasa |

---

## Fase 7 — Indicadores globales

> Existen parcialmente pero no cargan datos reales.

| Ítem | Estado | Descripción | Tarea |
|---|---|---|---|
| G02 | ⚠️ | Indicador global de flota se muestra pero no carga datos | `[AMBOS]` Conectar el indicador de ocupación global de vuelos al endpoint real; se espera un gauge o barra de progreso |
| G01 | ❌ | Sin indicador global de nivel de carga de la flota | `[FRONT]` Gauge o barra que muestre el % promedio de ocupación de todos los vuelos activos |
| G03 | ❌ | Sin indicador global de almacenes | `[FRONT]` Indicador que muestre el % promedio de ocupación de todos los almacenes |
| G04 | ❌ | El indicador de almacenes no tiene semáforo | `[FRONT]` El indicador global de almacenes debe usar colores semáforo según el % total |

### Multi-navegador

| Ítem | Estado | Descripción | Tarea |
|---|---|---|---|
| G06 | ⚠️ | Multi-navegador funciona en back pero el front no lo implementa | `[FRONT]` Permitir que múltiples pestañas/dispositivos se conecten simultáneamente y cada uno pueda interactuar de forma independiente en el mapa |

---

## Fase 8 — Reportes al finalizar escenario

> No existe ninguno. El back tiene los datos; falta exponerlos y renderizarlos.

| Ítem | Estado | Descripción | Tarea |
|---|---|---|---|
| G08 | ❌ | Sin reporte al finalizar simulación de periodo | `[BACK]` Endpoint `GET /api/v1/reports/simulation-summary` con métricas: maletas entregadas, retrasadas, rutas usadas, tiempo promedio |
| G08 | ❌ | — | `[FRONT]` Modal o pantalla de reporte que se muestra al terminar la simulación del periodo |
| G09 | ❌ | Sin reporte al cerrar operaciones día a día | `[BACK + FRONT]` Mismo patrón: generar y mostrar reporte al cerrar el escenario de operaciones |
| G10 | ❌ | Sin reporte al finalizar simulación de colapso | `[BACK + FRONT]` Generar y mostrar reporte del colapso con causa raíz identificada |

---

## Fase 9 — Documentación técnica

> No es código pero el profesor los marcó como "no entregó".

| Ítem | Estado | Descripción | Tarea |
|---|---|---|---|
| A22 | 🚫 | Sin diagrama de consumo de datos por bloques de tiempo | `[DOC]` Diagrama que explique cómo los datos se consumen en bloques de tiempo del eje de consumo (Sc) por cada salto del planificador (Sa). Incluir: Ta (tiempo algoritmo), Sa (salto planificador), Sc (salto consumo) |
| A23 | 🚫 | Sin diagrama de interacción multi-navegadores | `[DOC]` Diagrama de secuencia o arquitectura que muestre cómo interactúan 2+ navegadores simultáneos durante la simulación del periodo (WebSocket, estado compartido, acciones independientes) |
| A17 | — | Sin valor de Ta (tiempo de ejecución del algoritmo) | `[DOC]` Medir y documentar el tiempo que tarda el algoritmo planificador en ejecutarse |
| A18 | — | Sin valor de Sa (salto del algoritmo) | `[DOC]` Definir y documentar el valor del salto temporal del planificador |
| A19 | — | Sin valor de Sc (salto del eje de consumo) | `[DOC]` Definir y documentar el intervalo de tiempo en que el front consume datos del back |

---

## Lo que ya funciona bien ✅

### Configuración y carga de datos
- Mapa configurado desde `MapProvider` consumiendo `/api/v1/data/airports`
- Aeropuertos y vuelos cargados desde parsers (`AirportParser`, `FlightParser`, `ShipmentParser`)
- Datos base independientes de los escenarios (se cargan una sola vez)
- Datos completos sin reducción de registros

### Mapa base
- Mapa mundial con zoom in/out funcional
- Todos los aeropuertos visibles simultáneamente en sus coordenadas correctas
- Ícono de aeropuerto con color semáforo según stock (✅ para almacenes)
- Stock del aeropuerto al pasar el mouse (hover)
- Aviones visibles, con movimiento fluido y orientación coherente con la trayectoria
- Línea de tramo visible desde el inicio del vuelo
- Hora real actual mostrada correctamente
- Todo el sistema en español

### Algoritmo y planificación
- Algoritmo planificador "TASF" implementado
- Tiempo de permanencia mínima de maletas en aeropuertos respetado (G05)
- Datos de vuelos y rutas consultables vía `/api/v1/data/routes`

### Vinculación (parcial)
- Seleccionar vuelo en el panel → enfoca el avión en el mapa (F07 ✅)
- Seleccionar avión en el mapa → enfoca en el panel (F08 ✅)
