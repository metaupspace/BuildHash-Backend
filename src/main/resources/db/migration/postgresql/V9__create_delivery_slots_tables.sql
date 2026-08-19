CREATE TABLE slot_configurations (
    id UUID PRIMARY KEY,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    capacity INT NOT NULL DEFAULT 50,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE delivery_slot_counters (
    id UUID PRIMARY KEY,
    slot_id UUID NOT NULL REFERENCES slot_configurations(id),
    slot_date DATE NOT NULL,
    capacity INT NOT NULL,
    current_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_slot_counter_slot_date UNIQUE (slot_id, slot_date),
    CONSTRAINT chk_slot_counter_capacity CHECK (current_count >= 0 AND current_count <= capacity)
);

CREATE INDEX idx_slot_counters_date ON delivery_slot_counters(slot_date);

CREATE TABLE delivery_slot_locks (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    slot_id UUID NOT NULL REFERENCES slot_configurations(id),
    slot_date DATE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_slot_locks_user_status ON delivery_slot_locks(user_id, status);
CREATE INDEX idx_slot_locks_slot_date_status ON delivery_slot_locks(slot_id, slot_date, status);
CREATE INDEX idx_slot_locks_expires_at ON delivery_slot_locks(expires_at);

-- Seed standard daily slots
INSERT INTO slot_configurations (id, start_time, end_time, capacity, is_active) VALUES
    ('11111111-1111-1111-1111-111111111101', '09:00:00', '12:00:00', 50, true),
    ('11111111-1111-1111-1111-111111111102', '12:00:00', '15:00:00', 50, true),
    ('11111111-1111-1111-1111-111111111103', '15:00:00', '18:00:00', 50, true),
    ('11111111-1111-1111-1111-111111111104', '18:00:00', '21:00:00', 50, true);
