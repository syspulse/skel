-- Run as superuser against the default DB (postgres):
--   PGPASSWORD=root_pass psql -h localhost -U postgres -f db-create.sql
-- Then create the app schema in user_db:
--   PGPASSWORD=root_pass psql -h localhost -U postgres -d user_db -f db-schema.sql

CREATE DATABASE user_db;
CREATE USER user_user WITH PASSWORD 'user_pass';
GRANT CONNECT ON DATABASE user_db TO user_user;

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO user_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA user_schema TO user_user;
