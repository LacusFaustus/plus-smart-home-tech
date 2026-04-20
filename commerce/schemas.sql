-- Создание схем для каждого микросервиса
CREATE SCHEMA IF NOT EXISTS warehouse_db;
CREATE SCHEMA IF NOT EXISTS cart_db;
CREATE SCHEMA IF NOT EXISTS store_db;

-- Предоставление прав (опционально)
GRANT ALL PRIVILEGES ON SCHEMA warehouse_db TO postgres;
GRANT ALL PRIVILEGES ON SCHEMA cart_db TO postgres;
GRANT ALL PRIVILEGES ON SCHEMA store_db TO postgres;