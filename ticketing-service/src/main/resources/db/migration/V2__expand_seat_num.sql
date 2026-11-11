-- ============================================================================
-- FLASHTICKET DATABASE MIGRATION (V2__expand_seat_num.sql)
-- Expand row_num and seat_num columns to accommodate descriptive identifiers
-- ============================================================================

ALTER TABLE seats ALTER COLUMN row_num TYPE VARCHAR(50);
ALTER TABLE seats ALTER COLUMN seat_num TYPE VARCHAR(50);
