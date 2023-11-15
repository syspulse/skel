CREATE DATABASE odometer_db;
CREATE USER odometer_user WITH PASSWORD 'odometer_pass';
GRANT CONNECT ON DATABASE odometer_db TO odometer_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO odometer_user;

