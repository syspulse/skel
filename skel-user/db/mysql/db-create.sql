CREATE DATABASE IF NOT EXISTS user_db;
CREATE USER IF NOT EXISTS 'user_user'@'%' IDENTIFIED BY 'user_pass';
GRANT ALL PRIVILEGES ON user_db.* TO 'user_user'@'%' WITH GRANT OPTION;

