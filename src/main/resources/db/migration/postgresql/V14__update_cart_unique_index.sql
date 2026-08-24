DROP INDEX idx_carts_user_project;
CREATE UNIQUE INDEX idx_carts_user_project_type ON carts(user_id, COALESCE(project_id, '00000000-0000-0000-0000-000000000000'), cart_type);
