-- Staff seats bought through PayFast, so the billing page can show them.
ALTER TABLE product_checkouts ADD COLUMN IF NOT EXISTS staff_seat_count INTEGER;
ALTER TABLE billing_accounts ADD COLUMN IF NOT EXISTS staff_seat_count INTEGER;
