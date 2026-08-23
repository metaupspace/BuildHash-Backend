ALTER TABLE orders ADD COLUMN delivery_slot_lock_id UUID;
UPDATE orders SET delivery_slot_lock_id = gen_random_uuid();
ALTER TABLE orders ALTER COLUMN delivery_slot_lock_id SET NOT NULL;
