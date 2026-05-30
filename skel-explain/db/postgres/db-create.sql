CREATE DATABASE explain_db;
CREATE USER explain_user WITH PASSWORD 'explain_pass';
GRANT CONNECT ON DATABASE explain_db TO explain_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO explain_user;
