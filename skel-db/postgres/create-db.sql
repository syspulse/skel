CREATE DATABASE shop_db;
CREATE USER shop_user WITH PASSWORD 'shop_pass';
GRANT CONNECT ON DATABASE shop_db TO shop_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO shop_user;
