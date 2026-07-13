-- Full teardown of the standalone workflow database. Run as superuser.
-- NOTE: only removes the workflow_* tables owned by this product; external detector tables
-- (if present in this DB) are left to their owning product.
REASSIGN OWNED BY workflow_user TO postgres;  -- or another trusted role
DROP OWNED BY workflow_user;

DROP TABLE IF EXISTS workflow_schema;
DROP TABLE IF EXISTS workflow_config;
DROP TABLE IF EXISTS workflow_graf;

-- SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = 'workflow_db';
DROP DATABASE workflow_db WITH (FORCE);

DROP USER workflow_user;
