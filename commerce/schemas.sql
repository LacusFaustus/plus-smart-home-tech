-- Создание схем для каждого микросервиса
CREATE SCHEMA IF NOT EXISTS warehouse_db;
CREATE SCHEMA IF NOT EXISTS cart_db;
CREATE SCHEMA IF NOT EXISTS store_db;

-- Установка прав
GRANT ALL PRIVILEGES ON SCHEMA warehouse_db TO postgres;
GRANT ALL PRIVILEGES ON SCHEMA cart_db TO postgres;
GRANT ALL PRIVILEGES ON SCHEMA store_db TO postgres;

-- Установка search_path по умолчанию
ALTER DATABASE smarthome SET search_path TO warehouse_db, cart_db, store_db, public;