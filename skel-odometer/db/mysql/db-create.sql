CREATE DATABASE IF NOT EXISTS odometer_db;
CREATE USER IF NOT EXISTS 'odometer_user'@'%' IDENTIFIED BY 'odometer_pass';
GRANT ALL PRIVILEGES ON odometer_db.* TO 'odometer_user'@'%' WITH GRANT OPTION;

