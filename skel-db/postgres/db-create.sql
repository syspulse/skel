CREATE DATABASE medar_db;
CREATE USER medar_user WITH PASSWORD 'medar_pass';
GRANT CONNECT ON DATABASE medar_db TO medar_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO medar_user;

