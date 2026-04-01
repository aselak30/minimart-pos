-- MiniMart POS Ultimate - DATABASE COMPLETE INITIALIZATION
-- Version: 1.6.3 (Final Sync)
-- Compatible with MySQL 8.0+

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 1. Create Database
CREATE DATABASE IF NOT EXISTS minimart_pos_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE minimart_pos_db;

-- 2. Create Tables

-- Categories
CREATE TABLE IF NOT EXISTS categories (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    active      TINYINT(1) DEFAULT 1,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- Suppliers
CREATE TABLE IF NOT EXISTS suppliers (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(150) NOT NULL,
    contact_person VARCHAR(100),
    phone         VARCHAR(20),
    email         VARCHAR(100),
    address       TEXT,
    active        TINYINT(1) DEFAULT 1,
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- Products (SYNCHRONIZED)
CREATE TABLE IF NOT EXISTS products (
    id                   INT AUTO_INCREMENT PRIMARY KEY,
    barcode              VARCHAR(20)       NOT NULL UNIQUE,
    name                 VARCHAR(200)      NOT NULL,
    category_id          INT               NOT NULL,
    brand                VARCHAR(80),
    size_weight          VARCHAR(30),
    unit_price           DECIMAL(10,2)     NOT NULL,
    cost_price           DECIMAL(10,2)     NOT NULL DEFAULT 0.00,
    tax_rate             DECIMAL(5,2)      NOT NULL DEFAULT 0.00,
    discount_allowed     TINYINT(1)        NOT NULL DEFAULT 1,
    max_discount_percent DECIMAL(5,2),
    stock_quantity       DECIMAL(15,3)     NOT NULL DEFAULT 0.000,
    damaged_quantity     DECIMAL(15,3)     NOT NULL DEFAULT 0.000,
    reorder_level        DECIMAL(15,3)     NOT NULL DEFAULT 5.000,
    is_weight_based      TINYINT(1)        DEFAULT 0,
    weight_unit          VARCHAR(10)       DEFAULT 'kg',
    price_per_unit       DECIMAL(10,2),
    default_weight       DECIMAL(10,3)     DEFAULT 1.000,
    min_weight           DECIMAL(10,3),
    max_weight           DECIMAL(10,3),
    expiry_date          DATE,
    batch_number         VARCHAR(50),
    supplier_id          INT,
    location             VARCHAR(80),
    active               TINYINT(1)        NOT NULL DEFAULT 1,
    image_path           VARCHAR(255),
    description          TEXT,
    created_at           DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_prod_cat FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE RESTRICT,
    INDEX idx_barcode (barcode)
) ENGINE=InnoDB;

-- Users
CREATE TABLE IF NOT EXISTS users (
    id                   INT AUTO_INCREMENT PRIMARY KEY,
    username             VARCHAR(50)       NOT NULL UNIQUE,
    password_hash        VARCHAR(255)      NOT NULL,
    full_name            VARCHAR(100)      NOT NULL,
    role                 ENUM('ADMIN', 'CASHIER', 'SUPER_ADMIN') NOT NULL DEFAULT 'CASHIER',
    phone                VARCHAR(20),
    email                VARCHAR(100),
    active               TINYINT(1)        DEFAULT 1,
    last_login           DATETIME,
    created_at           DATETIME          DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME          DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    session_timeout_min INT               DEFAULT 60,
    theme_preference     VARCHAR(20)       DEFAULT 'DARK'
) ENGINE=InnoDB;

-- Customers
CREATE TABLE IF NOT EXISTS customers (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    phone         VARCHAR(20)  UNIQUE,
    email         VARCHAR(100),
    active        TINYINT(1)    DEFAULT 1,
    created_at    DATETIME      DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- Bills (SYNCHRONIZED with Java Model & DAO)
CREATE TABLE IF NOT EXISTS bills (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    bill_number      VARCHAR(30)       NOT NULL UNIQUE,
    cashier_id       INT               NOT NULL,
    customer_id      INT,
    machine_id       INT,
    subtotal         DECIMAL(10,2)     NOT NULL,
    discount_percent DECIMAL(5,2)      DEFAULT 0.00,
    discount_amount  DECIMAL(10,2)     DEFAULT 0.00,
    tax_amount       DECIMAL(10,2)     DEFAULT 0.00,
    total_amount     DECIMAL(10,2)     NOT NULL,
    paid_amount      DECIMAL(10,2)     NOT NULL,
    change_amount    DECIMAL(10,2)     NOT NULL,
    cost_total       DECIMAL(10,2)     DEFAULT 0.00, -- Needed for DAO
    profit_total     DECIMAL(10,2)     DEFAULT 0.00, -- Needed for DAO
    status           ENUM('DRAFT', 'FINALIZED', 'VOIDED', 'REFUNDED') NOT NULL DEFAULT 'DRAFT',
    payment_type     ENUM('CASH', 'CARD', 'MOBILE_MONEY', 'CREDIT', 'SPLIT') NOT NULL DEFAULT 'CASH',
    void_reason      TEXT,
    notes            TEXT,
    is_editable      TINYINT(1)        DEFAULT 1,
    deleted_by       INT,               -- Needed for Java model
    deleted_reason   TEXT,              -- Needed for Java model
    finalized_at     DATETIME,
    created_at       DATETIME          DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME          DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_bill_num (bill_number)
) ENGINE=InnoDB;

-- Bill Items
CREATE TABLE IF NOT EXISTS bill_items (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    bill_id          INT               NOT NULL,
    product_id       INT               NOT NULL,
    product_name     VARCHAR(200)      NOT NULL,
    product_barcode  VARCHAR(20)       NOT NULL,
    quantity         DECIMAL(15,3)     NOT NULL,
    unit_price       DECIMAL(10,2)     NOT NULL,
    original_price   DECIMAL(10,2)     NOT NULL,
    cost_price       DECIMAL(10,2)     NOT NULL,
    discount_percent DECIMAL(5,2)      DEFAULT 0.00,
    discount_amount  DECIMAL(10,2)     DEFAULT 0.00,
    tax_rate         DECIMAL(5,2)      DEFAULT 0.00,
    tax_amount       DECIMAL(10,2)     DEFAULT 0.00,
    line_total       DECIMAL(10,2)     NOT NULL,
    is_price_override TINYINT(1)       DEFAULT 0,
    is_weight_based  TINYINT(1)        DEFAULT 0,
    weight           DECIMAL(10,3),
    weight_unit      VARCHAR(10),
    CONSTRAINT fk_item_bill FOREIGN KEY (bill_id) REFERENCES bills(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 3. Initial Data

-- Users (Password: Admin@123)
REPLACE INTO users (username, password_hash, full_name, role) VALUES 
('admin', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQyCgRlKfJjH5NE8p9ZvQ8bKG', 'Administrator', 'ADMIN'),
('superadmin', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQyCgRlKfJjH5NE8p9ZvQ8bKG', 'Owner', 'SUPER_ADMIN');

-- Categories
REPLACE INTO categories (id, name) VALUES (1, 'General');

SET FOREIGN_KEY_CHECKS = 1;
