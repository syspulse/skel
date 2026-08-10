-- ============================================================================
-- wf-ext schema upgrade: add JsonSchema fields to workflow_schema / workflow_config
--   workflow_schema.schema     (jsonb)  -- JsonSchema of the workflow config (like detector_schema.schema)
--   workflow_schema.ui_schema  (jsonb)  -- UI hints for the schema         (like detector_schema.ui_schema)
--   workflow_config.config     (jsonb)  -- config values per the schema    (like detector.config)
--
-- All columns are NULLABLE / not mandatory: existing rows keep NULL and the code reads NULL as None.
-- Run once against an EXISTING database (fresh installs already get these columns from CREATE TABLE).
-- Idempotent: ADD COLUMN IF NOT EXISTS.
--
-- RUN AS THE TABLE OWNER (DB_USER). ALTER TABLE requires ownership; GRANT is not enough. Tables created
-- by WorkflowStoreDB (or by the fixed db-create.sql) are owned by DB_USER, so this works. If you hit
-- "ERROR: must be owner of table ...", the tables were created by another role (e.g. an old db-create.sql
-- run as the postgres superuser). Fix once AS SUPERUSER (e.g. skel-db/db-sql-root.sh, which passes DB_USER):
--   ALTER TABLE workflow_schema OWNER TO :"DB_USER";
--   ALTER TABLE workflow_config OWNER TO :"DB_USER";
--   ALTER TABLE workflow_graf   OWNER TO :"DB_USER";
-- then re-run this script as DB_USER.
-- ============================================================================

ALTER TABLE workflow_schema ADD COLUMN IF NOT EXISTS schema    JSONB;
ALTER TABLE workflow_schema ADD COLUMN IF NOT EXISTS ui_schema JSONB;

ALTER TABLE workflow_config ADD COLUMN IF NOT EXISTS config    JSONB;
