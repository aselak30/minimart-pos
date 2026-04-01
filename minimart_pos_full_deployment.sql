-- =============================================================================
-- MiniMart POS Ultimate - ULTIMATE DEPLOYMENT SCRIPT
-- Version: 3.0 (Official Schema + Stability Fixes + Security)
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 1. DATABASE & USER INITIALIZATION
CREATE DATABASE IF NOT EXISTS minimart_pos CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE minimart_pos;

-- Create pos_user if it doesn't exist (Matches App Default)
CREATE USER IF NOT EXISTS 'pos_user'@'localhost' IDENTIFIED BY '';
GRANT ALL PRIVILEGES ON minimart_pos.* TO 'pos_user'@'localhost';
FLUSH PRIVILEGES;

-- 2. USERS & SECURITY
CREATE TABLE IF NOT EXISTS users (
    id                      INT AUTO_INCREMENT PRIMARY KEY,
    username                VARCHAR(50)  NOT NULL UNIQUE,
    password_hash           VARCHAR(255) NOT NULL,
    full_name               VARCHAR(100) NOT NULL,
    role                    ENUM('ADMIN', 'CASHIER', 'SUPER_ADMIN') NOT NULL DEFAULT 'CASHIER',
    email                   VARCHAR(100),
    phone                   VARCHAR(20),
    active                  TINYINT(1)   NOT NULL DEFAULT 1,
    failed_login_attempts   INT          NOT NULL DEFAULT 0,
    locked_until            DATETIME,
    last_login              DATETIME,
    session_timeout_minutes INT          NOT NULL DEFAULT 60,
    theme_preference        VARCHAR(20)  DEFAULT 'DARK',
    last_theme_change       DATETIME,
    cash_limit              DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    daily_sales_target      DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username),
    INDEX idx_role     (role)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS user_permissions (
    user_id    INT         NOT NULL,
    permission VARCHAR(80) NOT NULL,
    granted_by INT,
    granted_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, permission),
    FOREIGN KEY (user_id)    REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (granted_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS login_audit (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    user_id    INT,
    username   VARCHAR(50)  NOT NULL,
    success    TINYINT(1)   NOT NULL,
    ip_address VARCHAR(45),
    machine_id VARCHAR(50),
    login_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- 3. INFRASTRUCTURE & SETTINGS
CREATE TABLE IF NOT EXISTS machines (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    machine_code VARCHAR(50) NOT NULL UNIQUE,
    machine_name VARCHAR(100),
    mac_address  VARCHAR(17),
    ip_address   VARCHAR(45),
    machine_type ENUM('CASHIER','ADMIN') NOT NULL DEFAULT 'CASHIER',
    active       TINYINT(1) NOT NULL DEFAULT 1,
    last_seen    DATETIME,
    created_at   DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS settings (
    setting_key   VARCHAR(100) NOT NULL PRIMARY KEY,
    setting_value TEXT,
    description   VARCHAR(255),
    updated_by    INT,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- 4. CATEGORIES, BRANDS & SUPPLIERS
CREATE TABLE IF NOT EXISTS categories (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(100) NOT NULL UNIQUE,
    parent_id  INT,
    description TEXT,
    active     TINYINT(1)   NOT NULL DEFAULT 1,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (parent_id) REFERENCES categories(id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS brands (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(100) NOT NULL UNIQUE,
    active     TINYINT(1)   NOT NULL DEFAULT 1,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS suppliers (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(150) NOT NULL,
    contact_name  VARCHAR(100),
    phone         VARCHAR(20),
    email         VARCHAR(100),
    address       TEXT,
    city          VARCHAR(80),
    active        TINYINT(1)   NOT NULL DEFAULT 1,
    notes         TEXT,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- 5. PRODUCTS & PRICING
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
    expiry_date          DATE,
    batch_number         VARCHAR(50),
    supplier_id          INT,
    location             VARCHAR(80),
    active               TINYINT(1)        NOT NULL DEFAULT 1,
    image_path           VARCHAR(255),
    description          TEXT,
    is_weight_based      TINYINT(1)        DEFAULT 0,
    weight_unit          VARCHAR(10)       DEFAULT 'kg',
    price_per_unit       DECIMAL(10,2),
    default_weight       DECIMAL(10,3)     DEFAULT 1.000,
    min_weight           DECIMAL(10,3)     DEFAULT 0.100,
    max_weight           DECIMAL(10,3)     DEFAULT 50.000,
    created_at           DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (category_id) REFERENCES categories(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS product_price_history (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    product_id   INT           NOT NULL,
    old_price    DECIMAL(10,2) NOT NULL,
    new_price    DECIMAL(10,2) NOT NULL,
    old_cost     DECIMAL(10,2),
    new_cost     DECIMAL(10,2),
    changed_by   INT,
    changed_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason       VARCHAR(255),
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 6. CUSTOMERS
CREATE TABLE IF NOT EXISTS customers (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    name             VARCHAR(150) NOT NULL,
    phone            VARCHAR(20)  UNIQUE,
    email            VARCHAR(100),
    address          TEXT,
    credit_limit     DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    credit_balance   DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    loyalty_points   INT           NOT NULL DEFAULT 0,
    active           TINYINT(1)    NOT NULL DEFAULT 1,
    notes            TEXT,
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_phone  (phone),
    INDEX idx_name   (name)
) ENGINE=InnoDB;

-- 7. BILLING (SYNCHRONIZED VOID/RETURN)
CREATE TABLE IF NOT EXISTS bills (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    bill_number      VARCHAR(30)       NOT NULL UNIQUE,
    cashier_id       INT               NOT NULL,
    customer_id      INT,
    machine_id       INT,
    subtotal         DECIMAL(10,2)     NOT NULL DEFAULT 0.00,
    discount_percent DECIMAL(5,2)      DEFAULT 0.00,
    discount_amount  DECIMAL(10,2)     DEFAULT 0.00,
    tax_amount       DECIMAL(10,2)     DEFAULT 0.00,
    total_amount     DECIMAL(10,2)     NOT NULL DEFAULT 0.00,
    paid_amount      DECIMAL(10,2)     NOT NULL DEFAULT 0.00,
    change_amount    DECIMAL(10,2)     NOT NULL DEFAULT 0.00,
    cost_total       DECIMAL(10,2)     DEFAULT 0.00, 
    profit_total     DECIMAL(10,2)     DEFAULT 0.00,
    payment_type     ENUM('CASH', 'CARD', 'MOBILE_MONEY', 'CREDIT', 'SPLIT') NOT NULL DEFAULT 'CASH',
    status           ENUM('DRAFT', 'FINALIZED', 'VOIDED', 'REFUNDED') NOT NULL DEFAULT 'DRAFT',
    void_reason      TEXT,
    notes            TEXT,
    is_editable      TINYINT(1)        DEFAULT 1,
    deleted_by       INT,
    deleted_reason   TEXT,
    finalized_at     DATETIME,
    created_at       DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (cashier_id)  REFERENCES users(id),
    FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE SET NULL,
    FOREIGN KEY (machine_id)  REFERENCES machines(id)  ON DELETE SET NULL,
    INDEX idx_bill_number   (bill_number),
    INDEX idx_created_at    (created_at),
    INDEX idx_status        (status)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS bill_items (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    bill_id          INT               NOT NULL,
    product_id       INT               NOT NULL,
    product_name     VARCHAR(200)      NOT NULL,
    product_barcode  VARCHAR(20)       NOT NULL,
    quantity         DECIMAL(15,3)     NOT NULL,
    unit_price       DECIMAL(10,2)     NOT NULL,
    original_price   DECIMAL(10,2)     NOT NULL,
    cost_price       DECIMAL(10,2)     NOT NULL DEFAULT 0.00,
    discount_percent DECIMAL(5,2)      DEFAULT 0.00,
    discount_amount  DECIMAL(10,2)     DEFAULT 0.00,
    tax_rate         DECIMAL(5,2)      DEFAULT 0.00,
    tax_amount       DECIMAL(10,2)     DEFAULT 0.00,
    line_total       DECIMAL(10,2)     NOT NULL,
    is_price_override TINYINT(1)       DEFAULT 0,
    is_weight_based  TINYINT(1)        DEFAULT 0,
    weight           DECIMAL(10,3),
    weight_unit      VARCHAR(10),
    FOREIGN KEY (bill_id)    REFERENCES bills(id)    ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id),
    INDEX idx_bill_id    (bill_id)
) ENGINE=InnoDB;

-- 8. STOCK & SHIFTS
CREATE TABLE IF NOT EXISTS stock_adjustments (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    product_id    INT           NOT NULL,
    adjustment    DECIMAL(15,3) NOT NULL,
    reason        ENUM('PURCHASE','SALE','ADJUSTMENT','DAMAGE','RETURN','TRANSFER','INITIAL') NOT NULL,
    reference_id  INT,
    old_quantity  DECIMAL(15,3) NOT NULL,
    new_quantity  DECIMAL(15,3) NOT NULL,
    adjusted_by   INT,
    adjusted_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notes         TEXT,
    FOREIGN KEY (product_id)  REFERENCES products(id) ON DELETE CASCADE,
    FOREIGN KEY (adjusted_by) REFERENCES users(id)    ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS shifts (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    cashier_id      INT           NOT NULL,
    machine_id      INT,
    start_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    end_time        DATETIME,
    opening_cash    DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    closing_cash    DECIMAL(10,2),
    total_sales     DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    total_bills     INT           NOT NULL DEFAULT 0,
    status          ENUM('OPEN','CLOSED') NOT NULL DEFAULT 'OPEN',
    notes           TEXT,
    FOREIGN KEY (cashier_id) REFERENCES users(id),
    FOREIGN KEY (machine_id) REFERENCES machines(id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- 9. AUDIT
CREATE TABLE IF NOT EXISTS audit_log (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    user_id     INT,
    username    VARCHAR(50),
    machine_id  INT,
    action      VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50),
    entity_id   INT,
    old_value   TEXT,
    new_value   TEXT,
    ip_address  VARCHAR(45),
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- 10. INITIAL SETUP DATA (Password: Admin@123)
REPLACE INTO users (id, username, password_hash, full_name, role, active) VALUES 
(1, 'admin', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQyCgRlKfJjH5NE8p9ZvQ8bKG', 'Administrator', 'ADMIN', 1),
(2, 'superadmin', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQyCgRlKfJjH5NE8p9ZvQ8bKG', 'Owner', 'SUPER_ADMIN', 1);

INSERT IGNORE INTO settings (setting_key, setting_value, description) VALUES
('company_name', 'minimartpos', 'Business name shown on receipts'),
('company_logo', 'images/logo.png', 'Path to company logo image'),
('receipt_logo', 'images/logo.png', 'Path to logo image for printed receipts'),
('currency_symbol', 'Rs.', 'Currency symbol'),
('currency_code', 'LKR', 'ISO currency code'),
('receipt_footer', 'Thank you for your visit!', 'Receipt footer message'),
('tax_inclusive', '0', '1 = prices include tax, 0 = tax added on top'),
('app_version', '1.6.0', 'Current app version');

INSERT IGNORE INTO categories (name) VALUES 
('Beverages'), ('Bakery'), ('Dairy'), ('Snacks'), ('Household'), ('Produce');

SET FOREIGN_KEY_CHECKS = 1;
