CREATE DATABASE ingest_db;
CREATE USER ingest_user WITH PASSWORD 'ingest_pass';
GRANT CONNECT ON DATABASE ingest_db TO ingest_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO ingest_user;
