-- Initialize the wf-ext database. Run via skel-db/db-create.sh, which sources db-env.sh and runs this
-- AS THE SUPERUSER (ROOT_USER) passing the env credentials as psql variables (DB_USER/DB_PASS/DB_DATABASE).
-- Credentials are NEVER hardcoded here - they live in db-env.sh (env).
--
-- The Workflow (wf-ext) product owns 3 tables in this database:
--   workflow_schema, workflow_config, workflow_graf
--
-- IMPORTANT (ownership): these tables are normally created at runtime by WorkflowStoreDB, which
-- connects AS DB_USER. Tables must therefore be OWNED by DB_USER - otherwise DB_USER cannot ALTER them
-- (db-upgrade.sql) and psql fails with "must be owner of table ...". GRANT ALL PRIVILEGES does NOT
-- confer ownership. So here we:
--   1) make DB_USER own the database + the public schema (so tables IT creates are ITS own), and
--   2) create the reference DDL below UNDER DB_USER (SET ROLE) so a manual run gets the right owner.
-- Each entity field maps to its own column; ONLY JsObject fields are jsonb.

-- Credentials come from env (db-env.sh: DB_USER/DB_PASS/DB_DATABASE), passed by the runner as psql
-- variables (skel-db/db-sql-root.sh: -v DB_USER=... -v DB_PASS=... -v DB_DATABASE=...). NEVER hardcode.
--   :"DB_USER" / :"DB_DATABASE" -> quoted identifier;  :'DB_PASS' -> quoted string literal.
CREATE USER :"DB_USER" WITH PASSWORD :'DB_PASS';
CREATE DATABASE :"DB_DATABASE" OWNER :"DB_USER";
GRANT ALL PRIVILEGES ON DATABASE :"DB_DATABASE" TO :"DB_USER";

\c :"DB_DATABASE"

-- DB_USER owns the schema so every table it (or WorkflowStoreDB) creates belongs to it
ALTER SCHEMA public OWNER TO :"DB_USER";
GRANT ALL ON SCHEMA public TO :"DB_USER";

-- create the wf-ext tables AS DB_USER -> owned by DB_USER (ALTER/upgrade works later).
-- (WorkflowStoreDB also runs these CREATE TABLE IF NOT EXISTS at startup as the same role.)
SET ROLE :"DB_USER";

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
  graph TEXT,
  schema JSONB,            -- JsObject (JsonSchema)
  ui_schema JSONB          -- JsObject (UI hints)
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
  meta TEXT,
  config JSONB             -- JsObject (config values per the schema)
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

RESET ROLE;

-- ---------------------------------------------------------------------------------------------
-- EXTERNAL tables: DetectorConfig -> "detector", DetectorSchema -> "detector_schema".
-- They are OWNED by a different product and MUST NOT be created/dropped here in shared
-- environments (WorkflowStoreDB never creates them). The reference DDL below (the actual upstream
-- schema) is ONLY for a standalone local/dev database.
--
-- WorkflowStoreDB maps these FLAT, without JOINs: detector.contract_id / schema_id are kept as
-- ids only (DetectorConfigContract / DetectorConfigSchema / destinations are NOT populated).
-- created_at/updated_at are `timestamp` (mapped to/from epoch-ms); tags/network_tags are text[].
-- ---------------------------------------------------------------------------------------------
-- CREATE TABLE public.detector_schema (
--   id serial4 NOT NULL,
--   created_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
--   updated_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
--   status text DEFAULT 'ACTIVE' NOT NULL,
--   "name" text NOT NULL,
--   "version" text NOT NULL,
--   "schema" jsonb NOT NULL,
--   tags _text DEFAULT '{}' NOT NULL,
--   description text DEFAULT '' NOT NULL,
--   faq jsonb DEFAULT '"[]"'::jsonb NOT NULL,
--   ui_schema jsonb DEFAULT '{}'::jsonb NOT NULL,
--   author text NULL,
--   icon text NULL,
--   network_tags _text DEFAULT '{}' NOT NULL,
--   title text NULL,
--   CONSTRAINT detector_schema_name_version UNIQUE (name, version),
--   CONSTRAINT detector_schema_pkey PRIMARY KEY (id)
-- );
-- CREATE TABLE public.detector (
--   id serial4 NOT NULL,
--   created_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
--   updated_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
--   status text DEFAULT 'ACTIVE' NOT NULL,
--   contract_id int4 NOT NULL,
--   "name" text NOT NULL,
--   "source" text NOT NULL,
--   schema_id int4 DEFAULT 1 NOT NULL,
--   tags _text DEFAULT '{}' NOT NULL,
--   config jsonb DEFAULT '{}' NOT NULL,
--   CONSTRAINT detector_pkey PRIMARY KEY (id)
-- );
-- -- FKs (upstream): detector.contract_id -> contract(id) ON DELETE CASCADE;
-- --                 detector.schema_id  -> detector_schema(id)
