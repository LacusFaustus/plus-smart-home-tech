-- Создание схем для каждого микросервиса
CREATE SCHEMA IF NOT EXISTS warehouse_db;
CREATE SCHEMA IF NOT EXISTS cart_db;
CREATE SCHEMA IF NOT EXISTS store_db;
CREATE SCHEMA IF NOT EXISTS order_db;
CREATE SCHEMA IF NOT EXISTS payment_db;
CREATE SCHEMA IF NOT EXISTS delivery_db;
CREATE SCHEMA IF NOT EXISTS security_db;  -- НОВАЯ схема для Spring Security

-- Установка прав
GRANT ALL PRIVILEGES ON SCHEMA warehouse_db TO postgres;
GRANT ALL PRIVILEGES ON SCHEMA cart_db TO postgres;
GRANT ALL PRIVILEGES ON SCHEMA store_db TO postgres;
GRANT ALL PRIVILEGES ON SCHEMA order_db TO postgres;
GRANT ALL PRIVILEGES ON SCHEMA payment_db TO postgres;
GRANT ALL PRIVILEGES ON SCHEMA delivery_db TO postgres;
GRANT ALL PRIVILEGES ON SCHEMA security_db TO postgres;

-- Таблицы для Spring Security JdbcUserDetailsManager
-- Должны быть в схеме security_db
SET search_path TO security_db;

-- Таблица users (по спецификации Spring Security)
CREATE TABLE IF NOT EXISTS users (
                                     id SERIAL PRIMARY KEY,
                                     username VARCHAR(45) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    enabled INT NOT NULL DEFAULT 1
    );

-- Таблица authorities (по спецификации Spring Security)
CREATE TABLE IF NOT EXISTS authorities (
                                           id SERIAL PRIMARY KEY,
                                           username VARCHAR(45) NOT NULL,
    authority VARCHAR(45) NOT NULL,
    CONSTRAINT fk_authorities_users FOREIGN KEY (username) REFERENCES users(username)
    );

-- Таблица для warehouse order_bookings
SET search_path TO warehouse_db;

CREATE TABLE IF NOT EXISTS order_bookings (
                                              id UUID PRIMARY KEY,
                                              order_id UUID NOT NULL,
                                              delivery_id UUID,
                                              total_weight DOUBLE PRECISION NOT NULL,
                                              total_volume DOUBLE PRECISION NOT NULL,
                                              fragile BOOLEAN DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS booking_products (
                                                booking_id UUID NOT NULL,
                                                product_id UUID NOT NULL,
                                                quantity BIGINT NOT NULL,
                                                PRIMARY KEY (booking_id, product_id),
    CONSTRAINT fk_booking_products_booking FOREIGN KEY (booking_id) REFERENCES order_bookings(id)
    );

-- Установка search_path по умолчанию
ALTER DATABASE smarthome SET search_path TO warehouse_db, cart_db, store_db, order_db, payment_db, delivery_db, security_db, public;