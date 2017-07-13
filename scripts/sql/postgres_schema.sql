-- Sample database schema for tourbillon
CREATE TABLE IF NOT EXISTS jobs (
    id VARCHAR PRIMARY KEY,
    data BYTEA,
    iv BYTEA
);

CREATE TABLE IF NOT EXISTS workflows (
    id VARCHAR PRIMARY KEY,
    data BYTEA,
    iv BYTEA
);

CREATE TABLE IF NOT EXISTS accounts (
    id VARCHAR PRIMARY KEY,
    data BYTEA,
    iv BYTEA
);

CREATE TABLE IF NOT EXISTS templates (
    id VARCHAR PRIMARY KEY,
    data BYTEA,
    iv BYTEA
);

CREATE TABLE IF NOT EXISTS events (
    id VARCHAR,
    job_id VARCHAR,
    "start" BIGINT NOT NULL,
    "interval" TEXT NULL,
    data BYTEA,
    iv BYTEA,
    is_expired BOOLEAN NOT NULL DEFAULT false,
    PRIMARY KEY (id, job_id, "start")
);

CREATE INDEX events_active_events_start
ON events ("start", is_expired);
