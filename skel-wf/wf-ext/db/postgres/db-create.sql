-- Run as superuser (e.g. PGPASSWORD=root_pass psql -h localhost -U postgres -f db-create.sql)
--
-- The Workflow (wf-ext) product owns 3 tables in this database:
--   workflow_schema, workflow_config, workflow_graf
-- These are also created automatically by WorkflowStoreDB at startup; the DDL is provided here for
-- manual / DBA setup. Each entity field maps to its own column; ONLY JsObject fields are jsonb.

CREATE DATABASE workflow_db;
CREATE USER workflow_user WITH PASSWORD 'workflow_pass';
GRANT CONNECT ON DATABASE workflow_db TO workflow_user;

\c workflow_db

CREATE TABLE IF NOT EXISTS workflow_schema (
  id BIGINT PRIMARY KEY,
  created_at BIGINT,
  updated_at BIGINT,
  status VARCHAR(64),
  name VARCHAR(255),
  version VARCHAR(64),
  title VARCHAR(255),
  description TEXT,
  author VARCHAR(255),
  icon TEXT,
  faq TEXT,
  tags TEXT,
  meta TEXT,
  graph TEXT
);

CREATE TABLE IF NOT EXISTS workflow_config (
  id BIGINT PRIMARY KEY,
  sid BIGINT,
  created_at BIGINT,
  updated_at BIGINT,
  status VARCHAR(64),
  name VARCHAR(255),
  version VARCHAR(64),
  title VARCHAR(255),
  description TEXT,
  author VARCHAR(255),
  icon TEXT,
  tags TEXT,
  graph TEXT,
  oid VARCHAR(128),
  pid VARCHAR(128),
  xid VARCHAR(128),
  meta TEXT
);

CREATE TABLE IF NOT EXISTS workflow_graf (
  id BIGINT PRIMARY KEY,
  sid BIGINT,
  cid BIGINT,
  nodes TEXT,
  links TEXT,
  meta TEXT,
  data JSONB               -- JsObject
);

CREATE INDEX IF NOT EXISTS workflow_config_xid ON workflow_config (lower(xid));
CREATE INDEX IF NOT EXISTS workflow_config_oid ON workflow_config (oid);

-- grant on the created tables (and any future ones)
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO workflow_user;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO workflow_user;

-- ---------------------------------------------------------------------------------------------
-- EXTERNAL tables: DetectorConfig -> "detector", DetectorSchema -> "detector_schema".
-- They are OWNED by a different product and MUST NOT be created/dropped here in shared
-- environments (WorkflowStoreDB never creates them). The reference DDL below is ONLY for a
-- standalone local/dev database. Only JsObject fields are jsonb (detector.config ;
-- detector_schema.schema, ui_schema).
-- ---------------------------------------------------------------------------------------------
-- CREATE TABLE IF NOT EXISTS detector (
--   id BIGINT PRIMARY KEY, created_at BIGINT, updated_at BIGINT, status VARCHAR(64),
--   contract TEXT, schema TEXT, name VARCHAR(255), source VARCHAR(255), tags TEXT,
--   config JSONB, destinations TEXT
-- );
-- CREATE TABLE IF NOT EXISTS detector_schema (
--   id BIGINT PRIMARY KEY, created_at BIGINT, updated_at BIGINT, status VARCHAR(64),
--   name VARCHAR(255), version VARCHAR(64), title VARCHAR(255), description TEXT, author VARCHAR(255),
--   icon TEXT, faq TEXT, tags TEXT, network_tags TEXT, schema JSONB, ui_schema JSONB
-- );
