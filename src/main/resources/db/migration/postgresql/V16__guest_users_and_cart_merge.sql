ALTER TABLE users ADD COLUMN is_guest BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE users ADD COLUMN merged_into_user_id UUID REFERENCES users(id);
