CREATE TABLE delivery_tracking_events (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_delivery_tracking_events_order_recorded ON delivery_tracking_events(order_id, recorded_at DESC);

ALTER TABLE orders
    ADD COLUMN driver_id VARCHAR(100),
    ADD COLUMN driver_phone VARCHAR(20);
