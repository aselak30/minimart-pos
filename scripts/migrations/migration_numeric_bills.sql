-- Migration: Convert BILL-YYYYMMDD-NNNN to YYYYMMDDNNNN
USE minimart_pos;

-- Update bills table
UPDATE bills 
SET bill_number = REPLACE(REPLACE(bill_number, 'BILL-', ''), '-', '')
WHERE bill_number LIKE 'BILL-%';

SELECT 'Migration completed: Existing bills updated to numeric format.' AS status;
