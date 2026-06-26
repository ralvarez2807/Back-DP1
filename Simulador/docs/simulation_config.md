Campos del POST /api/v1/simulations

Campo: dataSource
Tipo: string
Obligatorio: Sí
Valores: DB, MANUAL
Default / Notas: MANUAL no implementado aún
────────────────────────────────────────
Campo: solverTimingMode
Tipo: string
Obligatorio: Sí
Valores: REAL_TIME, PAUSE, EVENT_DRIVEN
Default / Notas: Solo REAL_TIME implementado
────────────────────────────────────────
Campo: optimizerMode
Tipo: string
Obligatorio: Sí
Valores: ALNS_ONLY, GENETIC_ONLY, ALNS_ACTIVE_GENETIC_EVAL,
  GENETIC_ACTIVE_ALNS_EVAL
Default / Notas:
────────────────────────────────────────
Campo: simStart
Tipo: Instant
Obligatorio: Sí (si DB)
Valores: ISO-8601
Default / Notas: Inicio del periodo simulado
────────────────────────────────────────
Campo: simEnd
Tipo: Instant
Obligatorio: Sí (si DB)
Valores: ISO-8601
Default / Notas: Fin del periodo simulado
────────────────────────────────────────
Campo: speedFactor
Tipo: double
Obligatorio: Sí (si DB)
Valores: > 0
Default / Notas: 100 ≈ 15 min de simulación por segundo real
────────────────────────────────────────
Campo: collapseOnFailure
Tipo: boolean
Obligatorio: No
Valores: true, false
Default / Notas: Default false — si true, la simulación para cuando el
  planificador falla

---
Campos internos (hardcodeados, no expuestos en la API)

┌──────────────────────┬────────┬─────────────────────────────────────┐
│        Campo         │ Valor  │            Qué controla             │
│                      │  fijo  │                                     │
├──────────────────────┼────────┼─────────────────────────────────────┤
│ minConnectionMinutes │ 10     │ Tiempo mínimo de conexión entre     │
│                      │        │ vuelos para una maleta en escala    │
├──────────────────────┼────────┼─────────────────────────────────────┤
│                      │        │ Tiempo desde que llega el vuelo     │
│ pickupMinutes        │ 10     │ hasta que la maleta está disponible │
│                      │        │  para recogida                      │
└──────────────────────┴────────┴─────────────────────────────────────┘

---
Significado de optimizerMode

┌──────────────────────────┬───────────────────────────────────────────┐
│          Valor           │              Comportamiento               │
├──────────────────────────┼───────────────────────────────────────────┤
│ ALNS_ONLY                │ Solo ALNS activo, asigna rutas            │
├──────────────────────────┼───────────────────────────────────────────┤
│ GENETIC_ONLY             │ Solo algoritmo genético activo            │
├──────────────────────────┼───────────────────────────────────────────┤
│ ALNS_ACTIVE_GENETIC_EVAL │ ALNS asigna rutas; Genético corre en      │
│                          │ paralelo solo para comparar métricas      │
├──────────────────────────┼───────────────────────────────────────────┤
│ GENETIC_ACTIVE_ALNS_EVAL │ Genético asigna rutas; ALNS corre en      │
│                          │ paralelo solo para comparar métricas      │
└──────────────────────────┴───────────────────────────────────────────┘

---
Si quieres exponer minConnectionMinutes y pickupMinutes como configurables desde la API, es trivial añadirlos al mismo flujo que ya tiene collapseOnFailure.