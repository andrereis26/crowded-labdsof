-- The predefined report categories (FR-034). Reference data that every
-- environment needs, so it is a migration and not demo data. Names are UI copy,
-- in European Portuguese. Personal categories are never shown publicly and
-- never trigger confirmation prompts (FR-059, FR-064).
INSERT INTO report_category (code, name, personal, prompts_enabled_by_default) VALUES
  ('CROWD_LEVEL', 'Lotação', false, true),
  ('QUEUE_LENGTH', 'Fila', false, true),
  ('ACCESSIBILITY_BARRIER', 'Acessibilidade', false, true),
  ('ON_SITE_PROBLEM', 'Problema no recinto', false, true),
  ('LOST_AND_FOUND', 'Perdidos e achados', true, false);
