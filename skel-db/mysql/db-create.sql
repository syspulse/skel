CREATE DATABASE IF NOT EXISTS service_db;
CREATE USER IF NOT EXISTS 'service_user'@'%' IDENTIFIED BY 'service_pass';
GRANT ALL PRIVILEGES ON service_db.* TO 'service_user'@'%' WITH GRANT OPTION;