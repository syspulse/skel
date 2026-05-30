CREATE DATABASE IF NOT EXISTS explain_db;
CREATE USER IF NOT EXISTS 'explain_user'@'%' IDENTIFIED BY 'explain_pass';
GRANT ALL PRIVILEGES ON explain_db.* TO 'explain_user'@'%' WITH GRANT OPTION;
