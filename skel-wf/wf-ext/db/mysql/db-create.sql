-- NOTE: WorkflowStoreDB is Postgres-first (jsonb). These MySQL scripts are provided for parity;
-- jsonb columns map to the MySQL JSON type. The Workflow product owns 3 tables:
--   workflow_schema, workflow_config, workflow_graf   (only JsObject fields are JSON)

CREATE DATABASE IF NOT EXISTS workflow_db;
CREATE USER IF NOT EXISTS 'workflow_user'@'%' IDENTIFIED BY 'workflow_pass';
GRANT ALL PRIVILEGES ON workflow_db.* TO 'workflow_user'@'%' WITH GRANT OPTION;

USE workflow_db;

CREATE TABLE IF NOT EXISTS workflow_schema (
  id BIGINT PRIMARY KEY, created_at BIGINT, updated_at BIGINT, status VARCHAR(64),
  name VARCHAR(255), version VARCHAR(64), title VARCHAR(255), description TEXT, author VARCHAR(255),
  icon TEXT, faq TEXT, tags TEXT, meta TEXT, graph TEXT
);

CREATE TABLE IF NOT EXISTS workflow_config (
  id BIGINT PRIMARY KEY, sid BIGINT, created_at BIGINT, updated_at BIGINT, status VARCHAR(64),
  name VARCHAR(255), version VARCHAR(64), title VARCHAR(255), description TEXT, author VARCHAR(255),
  icon TEXT, tags TEXT, graph TEXT, oid VARCHAR(128), pid VARCHAR(128), xid VARCHAR(128), meta TEXT
);

CREATE TABLE IF NOT EXISTS workflow_graf (
  id BIGINT PRIMARY KEY, sid BIGINT, cid BIGINT, nodes TEXT, links TEXT, meta TEXT, data JSON
);

CREATE INDEX workflow_config_xid ON workflow_config (xid);
CREATE INDEX workflow_config_oid ON workflow_config (oid);

-- ---------------------------------------------------------------------------------------------
-- EXTERNAL tables (owned by another product; NOT created in shared environments). Reference DDL
-- for a standalone local/dev database only:
-- CREATE TABLE IF NOT EXISTS detector (
--   id BIGINT PRIMARY KEY, created_at BIGINT, updated_at BIGINT, status VARCHAR(64),
--   contract TEXT, `schema` TEXT, name VARCHAR(255), source VARCHAR(255), tags TEXT,
--   config JSON, destinations TEXT
-- );
-- CREATE TABLE IF NOT EXISTS detector_schema (
--   id BIGINT PRIMARY KEY, created_at BIGINT, updated_at BIGINT, status VARCHAR(64),
--   name VARCHAR(255), version VARCHAR(64), title VARCHAR(255), description TEXT, author VARCHAR(255),
--   icon TEXT, faq TEXT, tags TEXT, network_tags TEXT, `schema` JSON, ui_schema JSON
-- );
