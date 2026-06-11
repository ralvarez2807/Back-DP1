-- ── Schemas ────────────────────────────────────────────────────────────────
CREATE SCHEMA IF NOT EXISTS reference;
CREATE SCHEMA IF NOT EXISTS simulation;
CREATE SCHEMA IF NOT EXISTS live;

-- ── REFERENCE — datos estáticos compartidos por ambos modos ────────────────

CREATE TABLE IF NOT EXISTS reference.airports (
    icao            CHAR(4)         PRIMARY KEY,
    city            VARCHAR(100)    NOT NULL,
    country         VARCHAR(100),
    continent       VARCHAR(50)     NOT NULL,
    short_name      VARCHAR(50),
    gmt_offset      SMALLINT        NOT NULL,
    capacity        INTEGER         NOT NULL,
    latitude        DECIMAL(9,6)    NOT NULL,
    longitude       DECIMAL(9,6)    NOT NULL
);

CREATE TABLE IF NOT EXISTS reference.flight_schedules (
    id                  VARCHAR(20)     PRIMARY KEY,   -- "SKBO-SEQM-19:00"
    origin_icao         CHAR(4)         NOT NULL REFERENCES reference.airports(icao),
    destination_icao    CHAR(4)         NOT NULL REFERENCES reference.airports(icao),
    departure_local     TIME            NOT NULL,
    arrival_local       TIME            NOT NULL,
    capacity            INTEGER         NOT NULL
);

-- ── SIMULATION — datos pre-cargados del dataset histórico ──────────────────

CREATE TABLE IF NOT EXISTS simulation.shipments (
    id                  VARCHAR(64)     NOT NULL,
    origin_icao         CHAR(4)         NOT NULL REFERENCES reference.airports(icao),
    destination_icao    CHAR(4)         NOT NULL REFERENCES reference.airports(icao),
    entry_utc           TIMESTAMPTZ     NOT NULL,
    quantity            SMALLINT        NOT NULL,
    client_id           VARCHAR(64),
    PRIMARY KEY (id, origin_icao)
);

CREATE TABLE IF NOT EXISTS simulation.cancellations (
    flight_schedule_id  VARCHAR(20)     NOT NULL REFERENCES reference.flight_schedules(id),
    departure_utc       TIMESTAMPTZ     NOT NULL,
    PRIMARY KEY (flight_schedule_id, departure_utc)
);

CREATE TABLE IF NOT EXISTS simulation.completed_flights (
    edge_id             VARCHAR(32)     PRIMARY KEY,   -- "SKBO-SEQM-19:00-20260102"
    flight_schedule_id  VARCHAR(20)     NOT NULL REFERENCES reference.flight_schedules(id),
    origin_icao         CHAR(4)         NOT NULL,
    destination_icao    CHAR(4)         NOT NULL,
    departure_utc       TIMESTAMPTZ     NOT NULL,
    arrival_utc         TIMESTAMPTZ     NOT NULL,
    capacity            INTEGER         NOT NULL,
    final_load          INTEGER         NOT NULL,
    cancelled           BOOLEAN         NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS simulation.delivered_baggages (
    baggage_id          VARCHAR(64)     PRIMARY KEY,   -- "<shipmentId>-B<n>"
    shipment_id         VARCHAR(64)     NOT NULL,
    origin_icao         CHAR(4)         NOT NULL,
    destination_icao    CHAR(4)         NOT NULL,
    entry_utc           TIMESTAMPTZ     NOT NULL,
    deadline_utc        TIMESTAMPTZ     NOT NULL,
    delivered_utc       TIMESTAMPTZ     NOT NULL,
    on_time             BOOLEAN         NOT NULL,
    FOREIGN KEY (shipment_id, origin_icao) REFERENCES simulation.shipments(id, origin_icao)
);

CREATE TABLE IF NOT EXISTS simulation.baggage_route_legs (
    baggage_id          VARCHAR(64)     NOT NULL REFERENCES simulation.delivered_baggages(baggage_id),
    leg_order           SMALLINT        NOT NULL,
    flight_edge_id      VARCHAR(32)     NOT NULL,
    PRIMARY KEY (baggage_id, leg_order)
);

-- ── LIVE — datos del modo tiempo real (operación en vivo) ──────────────────

CREATE TABLE IF NOT EXISTS live.shipments (
    id                  VARCHAR(64)     NOT NULL,
    origin_icao         CHAR(4)         NOT NULL REFERENCES reference.airports(icao),
    destination_icao    CHAR(4)         NOT NULL REFERENCES reference.airports(icao),
    entry_utc           TIMESTAMPTZ     NOT NULL,
    quantity            SMALLINT        NOT NULL,
    client_id           VARCHAR(64),
    PRIMARY KEY (id, origin_icao)
);

CREATE TABLE IF NOT EXISTS live.cancellations (
    flight_schedule_id  VARCHAR(20)     NOT NULL REFERENCES reference.flight_schedules(id),
    departure_utc       TIMESTAMPTZ     NOT NULL,
    PRIMARY KEY (flight_schedule_id, departure_utc)
);

CREATE TABLE IF NOT EXISTS live.completed_flights (
    edge_id             VARCHAR(32)     PRIMARY KEY,
    flight_schedule_id  VARCHAR(20)     NOT NULL REFERENCES reference.flight_schedules(id),
    origin_icao         CHAR(4)         NOT NULL,
    destination_icao    CHAR(4)         NOT NULL,
    departure_utc       TIMESTAMPTZ     NOT NULL,
    arrival_utc         TIMESTAMPTZ     NOT NULL,
    capacity            INTEGER         NOT NULL,
    final_load          INTEGER         NOT NULL,
    cancelled           BOOLEAN         NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS live.delivered_baggages (
    baggage_id          VARCHAR(64)     PRIMARY KEY,
    shipment_id         VARCHAR(64)     NOT NULL,
    origin_icao         CHAR(4)         NOT NULL,
    destination_icao    CHAR(4)         NOT NULL,
    entry_utc           TIMESTAMPTZ     NOT NULL,
    deadline_utc        TIMESTAMPTZ     NOT NULL,
    delivered_utc       TIMESTAMPTZ     NOT NULL,
    on_time             BOOLEAN         NOT NULL,
    FOREIGN KEY (shipment_id, origin_icao) REFERENCES live.shipments(id, origin_icao)
);

CREATE TABLE IF NOT EXISTS live.baggage_route_legs (
    baggage_id          VARCHAR(64)     NOT NULL REFERENCES live.delivered_baggages(baggage_id),
    leg_order           SMALLINT        NOT NULL,
    flight_edge_id      VARCHAR(32)     NOT NULL,
    PRIMARY KEY (baggage_id, leg_order)
);

-- ── SYSTEM ─────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.users (
    id              SERIAL          PRIMARY KEY,
    username        VARCHAR(50)     UNIQUE NOT NULL,
    password_hash   VARCHAR(72)     NOT NULL
);

-- ── INDEXES ────────────────────────────────────────────────────────────────

CREATE INDEX IF NOT EXISTS idx_sim_shipments_entry     ON simulation.shipments(entry_utc);
CREATE INDEX IF NOT EXISTS idx_sim_cancellations_sched ON simulation.cancellations(flight_schedule_id);
CREATE INDEX IF NOT EXISTS idx_sim_completed_dep       ON simulation.completed_flights(departure_utc);
CREATE INDEX IF NOT EXISTS idx_sim_delivered_utc       ON simulation.delivered_baggages(delivered_utc);
CREATE INDEX IF NOT EXISTS idx_sim_legs_baggage        ON simulation.baggage_route_legs(baggage_id);

CREATE INDEX IF NOT EXISTS idx_live_shipments_entry    ON live.shipments(entry_utc);
CREATE INDEX IF NOT EXISTS idx_live_cancellations_sched ON live.cancellations(flight_schedule_id);
CREATE INDEX IF NOT EXISTS idx_live_completed_dep      ON live.completed_flights(departure_utc);
CREATE INDEX IF NOT EXISTS idx_live_delivered_utc      ON live.delivered_baggages(delivered_utc);
CREATE INDEX IF NOT EXISTS idx_live_legs_baggage       ON live.baggage_route_legs(baggage_id);
