-- Run as superuser (e.g. PGPASSWORD=root_pass psql -h localhost -U postgres -f db-create.sql)

CREATE DATABASE explain_db;
CREATE USER explain_user WITH PASSWORD 'explain_pass';
GRANT CONNECT ON DATABASE explain_db TO explain_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO explain_user;

\c explain_db

-- Required for ?search=tgram (substring / ILIKE indexes). Superuser only.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
