-- Add price column to seats table.
-- Default is 5.00 (Standard price). Existing seats are updated
-- based on their seat_type after the column is added.
ALTER TABLE seats ADD COLUMN price DECIMAL(10, 2) NOT NULL DEFAULT 5.00;

-- Set prices based on existing seat types
UPDATE seats SET price = 5.00  WHERE UPPER(seat_type) = 'STANDARD';
UPDATE seats SET price = 10.00 WHERE UPPER(seat_type) = 'VIP';
UPDATE seats SET price = 15.00 WHERE UPPER(seat_type) = 'COUPLE';