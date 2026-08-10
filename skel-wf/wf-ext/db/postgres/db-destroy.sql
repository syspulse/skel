-- Full teardown of the standalone workflow database. Run as superuser.
-- NOTE: only removes the workflow_* tables owned by this product; external detector tables
-- (if present in this DB) are left to their owning product.
-- credentials from env (psql vars passed by skel-db/db-sql-root.sh); never hardcoded
REASSIGN OWNED BY :"DB_USER" TO CURRENT_USER;  -- to the superuser running this
DROP OWNED BY :"DB_USER";

DROP TABLE IF EXISTS workflow_schema;
DROP TABLE IF EXISTS workflow_config;
DROP TABLE IF EXISTS workflow_graf;

-- SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = :'DB_DATABASE';
DROP DATABASE :"DB_DATABASE" WITH (FORCE);

DROP USER :"DB_USER";
