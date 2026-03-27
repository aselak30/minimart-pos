-- ============================================================================
-- MiniMart POS - Create Database User
-- Run as MySQL root BEFORE importing schema.sql
-- Adjust the password 'posuser123' to your own secure password,
-- and update connection.properties to match.
-- ============================================================================

-- Create the restricted POS user (only has access to minimart_pos DB)
CREATE USER IF NOT EXISTS 'pos_user'@'localhost' IDENTIFIED BY 'posuser123';
CREATE USER IF NOT EXISTS 'pos_user'@'%'         IDENTIFIED BY 'posuser123';

-- Grant only what the app needs (no DROP, no root access)
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, INDEX
    ON minimart_pos.*
    TO 'pos_user'@'localhost';

GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, INDEX
    ON minimart_pos.*
    TO 'pos_user'@'%';

FLUSH PRIVILEGES;

SELECT 'Database user pos_user created successfully.' AS status;
