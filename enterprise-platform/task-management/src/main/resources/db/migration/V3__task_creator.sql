ALTER TABLE tasks ADD COLUMN created_by UUID;
CREATE INDEX idx_tasks_created_by ON tasks(created_by);