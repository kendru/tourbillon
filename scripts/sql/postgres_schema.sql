-- Sample database schema for tourbillon
CREATE TABLE IF NOT EXISTS jobs (
    id VARCHAR PRIMARY KEY,
    data TEXT
);

CREATE TABLE IF NOT EXISTS workflows (
    id VARCHAR PRIMARY KEY,
    data TEXT
);

CREATE TABLE IF NOT EXISTS accounts (
    id VARCHAR PRIMARY KEY,
    data TEXT
);

CREATE TABLE IF NOT EXISTS templates (
    id VARCHAR PRIMARY KEY,
    data TEXT
);

CREATE TABLE IF NOT EXISTS events (
    id VARCHAR,
    job_id VARCHAR,
    "start" BIGINT NOT NULL,
    "interval" INT NULL,
    data TEXT,
    is_expired BOOLEAN NOT NULL DEFAULT false,
    PRIMARY KEY (id, job_id, "start")
);

CREATE INDEX events_active_events_start
ON events ("start", is_expired);
