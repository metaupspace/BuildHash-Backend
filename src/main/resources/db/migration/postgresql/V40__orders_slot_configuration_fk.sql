-- H5.1: Foreign key constraint on orders.slot_id referencing slot_configurations(id)

ALTER TABLE orders
    ADD CONSTRAINT fk_orders_slot
    FOREIGN KEY (slot_id)
    REFERENCES slot_configurations(id)
    ON DELETE RESTRICT;
