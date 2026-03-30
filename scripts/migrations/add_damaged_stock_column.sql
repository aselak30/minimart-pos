-- Migration: Add damaged_quantity column to products table
ALTER TABLE products ADD COLUMN damaged_quantity DECIMAL(10,2) NOT NULL DEFAULT 0.00;
