-- Demo data (ADR 0012): enough to walk every Sprint 2 journey from an empty
-- database. Applied only when SEED_DEMO_DATA=true (local development and the
-- development and staging environments, and production while it simulates
-- them), after the migrations.
--
-- Every person, address and message is invented. The demo accounts all use
-- the password in database/README.md, stored only as a bcrypt hash (SR-013),
-- in the {id} format of Spring Security's DelegatingPasswordEncoder.
--
-- Fixed ids, so the apps and tests can point at them, and ON CONFLICT DO
-- NOTHING, so a changed file can run again. Times are relative to now(), so
-- the events stay upcoming, ongoing or past.
--
-- Ids by kind: 1… users, 2… organizations, 3… events, 4… event connections,
-- 5… event changes, 6… alerts, 7… incidents, 8… reports, 9… notifications,
-- a… everything else.

-- Users and organizations ------------------------------------------------------------

INSERT INTO app_user (id, organization_id, email, password_hash, role, created_at, location_consent_granted) VALUES
  ('10000000-0000-4000-8000-000000000001', NULL, 'admin@crowded.test',
   '{bcrypt}$2a$10$e3khyDlePjwPL44KuLuC8Ou8XRYZylqW9.LjCSiUzXTJBn1a8p3AW', 'ADMINISTRATOR', now() - interval '90 days', NULL),
  ('10000000-0000-4000-8000-000000000011', NULL, 'ana@crowded.test',
   '{bcrypt}$2a$10$Pej545frW7DJVBXNl6Nv0uNJK5aYDLMUMp8/heRVVFYfx/nFQyO2.', 'PARTICIPANT', now() - interval '60 days', true),
  ('10000000-0000-4000-8000-000000000012', NULL, 'bruno@crowded.test',
   '{bcrypt}$2a$10$4I61NORkgLlIGUf69eD1Ne7sPxRnEdIcCFHcHHWZsjlXP91EYfF5e', 'PARTICIPANT', now() - interval '45 days', true),
  ('10000000-0000-4000-8000-000000000013', NULL, 'carla@crowded.test',
   '{bcrypt}$2a$10$E6ptIDjgyEv.19M3kLhzVuXpCp2S1Oc.efeAzHX5Eq02ra1tk0SuG', 'PARTICIPANT', now() - interval '20 days', false)
ON CONFLICT DO NOTHING;

INSERT INTO organization (id, verified_by_user_id, name, verification_status, verified_at) VALUES
  ('20000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001',
   'Ribeira Live', 'VERIFIED', now() - interval '80 days'),
  ('20000000-0000-4000-8000-000000000002', NULL, 'Associação Sons do Minho', 'PENDING', NULL),
  ('20000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000001',
   'Eventos Fantasma, Lda.', 'REJECTED', now() - interval '30 days')
ON CONFLICT DO NOTHING;

INSERT INTO app_user (id, organization_id, email, password_hash, role, created_at, location_consent_granted) VALUES
  ('10000000-0000-4000-8000-000000000021', '20000000-0000-4000-8000-000000000001', 'equipa@ribeira-live.test',
   '{bcrypt}$2a$10$07ZwH814lzj054wlxFyUDOZDGcmOO8u2YjP/CGcXZl8ZUAp9dg5OW', 'ORGANIZATION_MEMBER', now() - interval '85 days', NULL),
  ('10000000-0000-4000-8000-000000000022', '20000000-0000-4000-8000-000000000002', 'geral@sons-do-minho.test',
   '{bcrypt}$2a$10$C5KIKGXFd06/3JWm1JBiYOFU2sv0tNWzpyx/l14I5RYw0Vlq1YJsa', 'ORGANIZATION_MEMBER', now() - interval '5 days', NULL)
ON CONFLICT DO NOTHING;

-- Events, one in every state ---------------------------------------------------------

INSERT INTO event (
  id, organization_id, name, description, tentative_period,
  start_date_time, doors_open_at, end_date_time, status, capacity, visibility, origin,
  ticketing_source_event_id, last_synced_at, venue_name, address, latitude, longitude,
  crowd_level, head_count, crowd_provenance_source_type, crowd_provenance_source_name,
  crowd_provenance_last_updated_at, crowd_provenance_confirmed
) VALUES
  ('30000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001',
   'Festival Margens do Douro', 'Dois dias de música à beira-rio.', NULL,
   date_trunc('hour', now()) + interval '12 days 18 hours', date_trunc('hour', now()) + interval '12 days 16 hours',
   date_trunc('hour', now()) + interval '13 days 23 hours', 'SCHEDULED', 8000, 'TICKETED', 'ORGANIZATION',
   'TKT-1001', now() - interval '10 minutes', 'Cais da Ribeira', 'Cais da Ribeira, 4050-510 Porto', 41.1407, -8.6130,
   NULL, NULL, NULL, NULL, NULL, NULL),
  ('30000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000001',
   'Concerto na Avenida', 'Concerto gratuito ao ar livre.', NULL,
   date_trunc('hour', now()) - interval '1 hour', date_trunc('hour', now()) - interval '2 hours',
   date_trunc('hour', now()) + interval '3 hours', 'ONGOING', 15000, 'PUBLIC', 'ORGANIZATION',
   NULL, NULL, 'Avenida dos Aliados', 'Avenida dos Aliados, 4000-064 Porto', 41.1488, -8.6110,
   'HIGH', 11200, 'ORGANIZATION', 'Ribeira Live', now() - interval '5 minutes', true),
  ('30000000-0000-4000-8000-000000000003', '20000000-0000-4000-8000-000000000001',
   'Noite de Fado no Palácio', 'Fado com vista para o rio.', NULL,
   date_trunc('hour', now()) + interval '30 days 21 hours', date_trunc('hour', now()) + interval '30 days 20 hours',
   date_trunc('hour', now()) + interval '30 days 23 hours', 'POSTPONED', 600, 'TICKETED', 'ORGANIZATION',
   NULL, NULL, 'Palácio da Bolsa', 'Rua de Ferreira Borges, 4050-253 Porto', 41.1413, -8.6156,
   NULL, NULL, NULL, NULL, NULL, NULL),
  ('30000000-0000-4000-8000-000000000004', NULL,
   'Arraial de São João', 'Arraial popular com sardinhas e música.', NULL,
   date_trunc('hour', now()) + interval '5 days 19 hours', NULL,
   date_trunc('hour', now()) + interval '6 days 2 hours', 'RELOCATED', NULL, 'PUBLIC', 'MUNICIPAL_AGENDA',
   NULL, now() - interval '1 hour', 'Jardim do Palácio de Cristal', 'Rua de Dom Manuel II, 4050-346 Porto', 41.1480, -8.6250,
   NULL, NULL, NULL, NULL, NULL, NULL),
  ('30000000-0000-4000-8000-000000000005', NULL,
   'Rock no Parque', 'Três bandas locais no parque da cidade.', NULL,
   date_trunc('hour', now()) + interval '8 days 17 hours', date_trunc('hour', now()) + interval '8 days 16 hours',
   date_trunc('hour', now()) + interval '8 days 23 hours', 'CANCELLED', 3000, 'TICKETED', 'TICKETING_PLATFORM',
   'TKT-2002', now() - interval '20 minutes', 'Parque da Cidade', 'Estrada Interior da Circunvalação, 4100-083 Porto', 41.1700, -8.6780,
   NULL, NULL, NULL, NULL, NULL, NULL),
  ('30000000-0000-4000-8000-000000000006', '20000000-0000-4000-8000-000000000001',
   'Mercado de Natal', 'Feira de artesanato e gastronomia.', NULL,
   date_trunc('hour', now()) - interval '20 days', date_trunc('hour', now()) - interval '20 days',
   date_trunc('hour', now()) - interval '18 days', 'ENDED', 5000, 'PUBLIC', 'ORGANIZATION',
   NULL, NULL, 'Praça da Batalha', 'Praça da Batalha, 4000-101 Porto', 41.1455, -8.6060,
   NULL, NULL, NULL, NULL, NULL, NULL),
  ('30000000-0000-4000-8000-000000000007', '20000000-0000-4000-8000-000000000001',
   'Festival de Jazz da Ribeira', 'Uma nova edição, se houver interesse.', 'Primavera do próximo ano',
   NULL, NULL, NULL, 'PROPOSED', NULL, 'TICKETED', 'ORGANIZATION',
   NULL, NULL, NULL, NULL, NULL, NULL,
   NULL, NULL, NULL, NULL, NULL, NULL),
  ('30000000-0000-4000-8000-000000000008', NULL,
   'Feira do Livro de Braga', 'Livreiros e editoras na avenida central.', NULL,
   date_trunc('hour', now()) + interval '20 days 10 hours', NULL,
   date_trunc('hour', now()) + interval '27 days 20 hours', 'SCHEDULED', NULL, 'PUBLIC', 'MUNICIPAL_AGENDA',
   NULL, now() - interval '2 hours', 'Avenida Central', 'Avenida Central, 4710-228 Braga', 41.5510, -8.4230,
   NULL, NULL, NULL, NULL, NULL, NULL)
ON CONFLICT DO NOTHING;

INSERT INTO event_change (
  id, event_id, type, severity, previous_value, new_value, ticket_still_valid, refund_info_url,
  provenance_source_type, provenance_source_name, provenance_last_updated_at, provenance_confirmed,
  corroborating_source_type, corroborated_at
) VALUES
  ('50000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000003', 'POSTPONEMENT', 'CRITICAL',
   '{"status": "SCHEDULED", "startDateTime": "anterior"}', '{"status": "POSTPONED"}', true, NULL,
   'ORGANIZATION', 'Ribeira Live', now() - interval '2 days', true, NULL, NULL),
  ('50000000-0000-4000-8000-000000000002', '30000000-0000-4000-8000-000000000004', 'VENUE_CHANGE', 'WARNING',
   '{"venueName": "Praça da Ribeira"}', '{"venueName": "Jardim do Palácio de Cristal"}', NULL, NULL,
   'OFFICIAL_SOURCE', 'Agenda municipal', now() - interval '1 day', true, NULL, NULL),
  ('50000000-0000-4000-8000-000000000003', '30000000-0000-4000-8000-000000000005', 'CANCELLATION', 'CRITICAL',
   '{"status": "SCHEDULED"}', '{"status": "CANCELLED"}', false, 'https://bilheteira.example/reembolsos',
   'OFFICIAL_SOURCE', 'Bilheteira', now() - interval '6 hours', true, NULL, NULL),
  ('50000000-0000-4000-8000-000000000004', '30000000-0000-4000-8000-000000000001', 'TIME_CHANGE', 'INFO',
   '{"doorsOpenAt": "17:00"}', '{"doorsOpenAt": "16:00"}', true, NULL,
   'ORGANIZATION', 'Ribeira Live', now() - interval '3 days', true, 'OFFICIAL_SOURCE', now() - interval '3 days')
ON CONFLICT DO NOTHING;

INSERT INTO ownership_request (id, organization_id, event_id, decided_by_user_id, status, requested_at, decided_at) VALUES
  ('a0000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000002',
   '30000000-0000-4000-8000-000000000008', NULL, 'PENDING', now() - interval '1 day', NULL)
ON CONFLICT DO NOTHING;

-- Tickets and follows -------------------------------------------------------------------

INSERT INTO event_connection (id, user_id, event_id, connected_at, last_prompted_at) VALUES
  ('40000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000011', '30000000-0000-4000-8000-000000000001', now() - interval '15 days', NULL),
  ('40000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000011', '30000000-0000-4000-8000-000000000003', now() - interval '25 days', NULL),
  ('40000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000011', '30000000-0000-4000-8000-000000000002', now() - interval '3 days', now() - interval '20 minutes'),
  ('40000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000012', '30000000-0000-4000-8000-000000000002', now() - interval '2 days', NULL),
  ('40000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000012', '30000000-0000-4000-8000-000000000005', now() - interval '10 days', NULL),
  ('40000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000013', '30000000-0000-4000-8000-000000000001', now() - interval '4 days', NULL),
  ('40000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000013', '30000000-0000-4000-8000-000000000004', now() - interval '1 day', NULL),
  ('40000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000013', '30000000-0000-4000-8000-000000000002', now() - interval '1 day', now() - interval '20 minutes')
ON CONFLICT DO NOTHING;

INSERT INTO ticket (id, gate, sector, seat, entry_code, import_method, status) VALUES
  ('40000000-0000-4000-8000-000000000001', 'Porta 2', 'Plateia', NULL, NULL, 'PDF_UPLOAD', 'CONFIRMED'),
  ('40000000-0000-4000-8000-000000000002', NULL, 'Sala Árabe', 'F12', NULL, 'EMAIL_PASTE', 'CONFIRMED'),
  ('40000000-0000-4000-8000-000000000005', 'Porta Norte', NULL, NULL, NULL, 'MANUAL_ENTRY', 'CONFIRMED'),
  ('40000000-0000-4000-8000-000000000006', NULL, NULL, NULL, NULL, 'PDF_UPLOAD', 'PENDING_CONFIRMATION')
ON CONFLICT DO NOTHING;

INSERT INTO follow (id, going) VALUES
  ('40000000-0000-4000-8000-000000000003', true),
  ('40000000-0000-4000-8000-000000000004', true),
  ('40000000-0000-4000-8000-000000000007', false),
  ('40000000-0000-4000-8000-000000000008', true)
ON CONFLICT DO NOTHING;

INSERT INTO ticket_extraction (id, ticket_id, extracted_fields, field_confidence, ai_generated, created_at) VALUES
  ('a0000000-0000-4000-8000-000000000011', '40000000-0000-4000-8000-000000000006',
   '{"eventName": "Festival Margens do Douro", "venueName": "Cais da Ribeira", "date": "dia 1"}',
   '{"eventName": 0.94, "venueName": 0.88, "date": 0.61}', true, now() - interval '4 days')
ON CONFLICT DO NOTHING;

INSERT INTO event_match_candidate (id, ticket_extraction_id, event_id, confidence) VALUES
  ('a0000000-0000-4000-8000-000000000021', 'a0000000-0000-4000-8000-000000000011', '30000000-0000-4000-8000-000000000001', 0.91)
ON CONFLICT DO NOTHING;

-- On-site information -----------------------------------------------------------------------

INSERT INTO queue_time (
  id, event_id, kind, point_name, wait_minutes,
  provenance_source_type, provenance_source_name, provenance_last_updated_at, provenance_confirmed
) VALUES
  ('a0000000-0000-4000-8000-000000000031', '30000000-0000-4000-8000-000000000002', 'ENTRANCE', 'Entrada Sul', 15,
   'ORGANIZATION', 'Ribeira Live', now() - interval '6 minutes', true),
  ('a0000000-0000-4000-8000-000000000032', '30000000-0000-4000-8000-000000000002', 'BAR', 'Bar central', 25,
   'PARTICIPANT_ESTIMATE', 'Participantes', now() - interval '12 minutes', false),
  ('a0000000-0000-4000-8000-000000000033', '30000000-0000-4000-8000-000000000002', 'TOILET', 'WC Norte', 5,
   'ORGANIZATION', 'Ribeira Live', now() - interval '8 minutes', true)
ON CONFLICT DO NOTHING;

INSERT INTO weather_forecast (
  id, event_id, date, conditions, temperature, severe_weather,
  provenance_source_type, provenance_source_name, provenance_last_updated_at, provenance_confirmed
) VALUES
  ('a0000000-0000-4000-8000-000000000041', '30000000-0000-4000-8000-000000000001', (now() + interval '12 days')::date,
   'Aguaceiros fracos', 17.5, false, 'EXTERNAL_SERVICE', 'Serviço meteorológico', now() - interval '1 hour', true),
  ('a0000000-0000-4000-8000-000000000042', '30000000-0000-4000-8000-000000000004', (now() + interval '5 days')::date,
   'Trovoada forte', 22.0, true, 'EXTERNAL_SERVICE', 'Serviço meteorológico', now() - interval '1 hour', true)
ON CONFLICT DO NOTHING;

-- Reports, confirmations and incidents -----------------------------------------------------------

INSERT INTO event_category_setting (id, event_id, report_category_code, prompts_enabled) VALUES
  ('a0000000-0000-4000-8000-000000000051', '30000000-0000-4000-8000-000000000002', 'ACCESSIBILITY_BARRIER', false)
ON CONFLICT DO NOTHING;

INSERT INTO incident (id, event_id, summary, status, grouping_method, created_at) VALUES
  ('70000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000002',
   'Entrada Sul muito lotada', 'ACKNOWLEDGED', 'AI_SIMILARITY', now() - interval '25 minutes')
ON CONFLICT DO NOTHING;

INSERT INTO report (
  id, event_id, user_id, report_category_code, incident_id, description,
  reported_crowd_level, reported_queue_point, reported_wait_minutes, reported_at, location_verified,
  confidence_score, confidence_level, confidence_computed_at
) VALUES
  ('80000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000012',
   'CROWD_LEVEL', '70000000-0000-4000-8000-000000000001', 'Muita gente junto à entrada sul.',
   'FULL', NULL, NULL, now() - interval '30 minutes', true, 0.82, 'HIGH', now() - interval '15 minutes'),
  ('80000000-0000-4000-8000-000000000002', '30000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000011',
   'QUEUE_LENGTH', '70000000-0000-4000-8000-000000000001', NULL,
   NULL, 'Entrada Sul', 20, now() - interval '28 minutes', true, 0.55, 'MEDIUM', now() - interval '15 minutes'),
  ('80000000-0000-4000-8000-000000000003', '30000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000013',
   'LOST_AND_FOUND', NULL, 'Perdi um casaco azul perto do palco.',
   NULL, NULL, NULL, now() - interval '10 minutes', false, 0.0, 'LOW', now() - interval '10 minutes')
ON CONFLICT DO NOTHING;

INSERT INTO confirmation (id, report_id, user_id, answer, answered_at, location_verified) VALUES
  ('a0000000-0000-4000-8000-000000000061', '80000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000011',
   'YES', now() - interval '18 minutes', true),
  ('a0000000-0000-4000-8000-000000000062', '80000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000013',
   'YES', now() - interval '16 minutes', true)
ON CONFLICT DO NOTHING;

INSERT INTO recognition (id, report_id, impact, reached_users, granted_at) VALUES
  ('a0000000-0000-4000-8000-000000000071', '80000000-0000-4000-8000-000000000001',
   'O teu reporte ajudou a organização a abrir uma segunda entrada.', 240, now() - interval '5 minutes')
ON CONFLICT DO NOTHING;

-- Organizer communication ----------------------------------------------------------------------------

INSERT INTO alert (
  id, event_id, organization_id, incident_id, type, severity, message, status, sent_at, resolved_at, recipients_count
) VALUES
  ('60000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000001',
   '70000000-0000-4000-8000-000000000001', 'ENTRANCE_CLOSURE', 'WARNING',
   'A Entrada Sul está fechada. Usem a Entrada Norte, na Rua do Almada.', 'SENT', now() - interval '12 minutes', NULL, 3),
  ('60000000-0000-4000-8000-000000000002', '30000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001',
   NULL, 'WEATHER_WARNING', 'INFO',
   'Pode chover no primeiro dia. Tragam um impermeável.', 'DRAFT', NULL, NULL, NULL)
ON CONFLICT DO NOTHING;

INSERT INTO alert_draft (id, alert_id, organizer_input, draft_text, facts_used, ai_generated) VALUES
  ('a0000000-0000-4000-8000-000000000081', '60000000-0000-4000-8000-000000000002',
   'avisar que pode chover', 'Pode chover no primeiro dia. Tragam um impermeável.',
   '[{"fact": "Aguaceiros fracos", "source": "Serviço meteorológico"}]', true)
ON CONFLICT DO NOTHING;

INSERT INTO event_survey (id, event_id, type, text, urgent, published_at, closes_at) VALUES
  ('a0000000-0000-4000-8000-000000000091', '30000000-0000-4000-8000-000000000002', 'CHOICE',
   'Qual o melhor sítio para um novo bar?', false, now() - interval '40 minutes', now() + interval '2 hours')
ON CONFLICT DO NOTHING;

INSERT INTO survey_option (id, event_survey_id, label) VALUES
  ('a0000000-0000-4000-8000-000000000092', 'a0000000-0000-4000-8000-000000000091', 'Junto ao palco'),
  ('a0000000-0000-4000-8000-000000000093', 'a0000000-0000-4000-8000-000000000091', 'Junto à Entrada Norte')
ON CONFLICT DO NOTHING;

INSERT INTO survey_response (id, event_survey_id, user_id, answer, responded_at) VALUES
  ('a0000000-0000-4000-8000-000000000094', 'a0000000-0000-4000-8000-000000000091', '10000000-0000-4000-8000-000000000012',
   'Junto à Entrada Norte', now() - interval '30 minutes')
ON CONFLICT DO NOTHING;

INSERT INTO interest_poll (id, organization_id, event_id, question, public_slug, opens_at, closes_at) VALUES
  ('a0000000-0000-4000-8000-0000000000a1', '20000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000007',
   'Que fim de semana preferes para o Festival de Jazz?', 'jazz-ribeira', now() - interval '3 days', now() + interval '11 days')
ON CONFLICT DO NOTHING;

INSERT INTO poll_option (id, interest_poll_id, label, position) VALUES
  ('a0000000-0000-4000-8000-0000000000a2', 'a0000000-0000-4000-8000-0000000000a1', 'Início de abril', 1),
  ('a0000000-0000-4000-8000-0000000000a3', 'a0000000-0000-4000-8000-0000000000a1', 'Fim de abril', 2),
  ('a0000000-0000-4000-8000-0000000000a4', 'a0000000-0000-4000-8000-0000000000a1', 'Maio', 3)
ON CONFLICT DO NOTHING;

INSERT INTO poll_response (id, interest_poll_id, user_id, poll_option_id, responded_at) VALUES
  ('a0000000-0000-4000-8000-0000000000a5', 'a0000000-0000-4000-8000-0000000000a1', '10000000-0000-4000-8000-000000000011',
   'a0000000-0000-4000-8000-0000000000a3', now() - interval '2 days')
ON CONFLICT DO NOTHING;

-- Audit log -------------------------------------------------------------------------------------------

INSERT INTO audit_log_entry (id, actor_id, event_id, action, entity_type, entity_id, details, occurred_at) VALUES
  ('a0000000-0000-4000-8000-0000000000b1', '10000000-0000-4000-8000-000000000001', NULL,
   'ORGANIZATION_VERIFIED', 'Organization', '20000000-0000-4000-8000-000000000001', '{"status": "VERIFIED"}', now() - interval '80 days'),
  ('a0000000-0000-4000-8000-0000000000b2', '10000000-0000-4000-8000-000000000021', '30000000-0000-4000-8000-000000000003',
   'EVENT_CHANGE_PUBLISHED', 'EventChange', '50000000-0000-4000-8000-000000000001', '{"type": "POSTPONEMENT"}', now() - interval '2 days'),
  ('a0000000-0000-4000-8000-0000000000b3', '10000000-0000-4000-8000-000000000021', '30000000-0000-4000-8000-000000000002',
   'ALERT_SENT', 'Alert', '60000000-0000-4000-8000-000000000001', '{"recipients": 3}', now() - interval '12 minutes')
ON CONFLICT DO NOTHING;

-- Notifications ----------------------------------------------------------------------------------------

INSERT INTO user_notification_setting (id, user_id, type, enabled) VALUES
  ('a0000000-0000-4000-8000-0000000000c1', '10000000-0000-4000-8000-000000000011', 'ORGANIZATION_SURVEY', false),
  ('a0000000-0000-4000-8000-0000000000c2', '10000000-0000-4000-8000-000000000011', 'CONFIRMATION_REQUEST', true)
ON CONFLICT DO NOTHING;

INSERT INTO event_notification_setting (id, event_connection_id, type, enabled) VALUES
  ('a0000000-0000-4000-8000-0000000000c3', '40000000-0000-4000-8000-000000000003', 'CONFIRMATION_REQUEST', false)
ON CONFLICT DO NOTHING;

INSERT INTO notification (
  id, user_id, event_id, type, cause_id, title, body, deduplication_key, created_at, delivered_at, read_at
) VALUES
  ('90000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000011', '30000000-0000-4000-8000-000000000003',
   'OFFICIAL_CHANGE', '50000000-0000-4000-8000-000000000001', 'Noite de Fado no Palácio foi adiado',
   'O teu bilhete continua válido para a nova data.', 'demo-official-change-ana-fado',
   now() - interval '2 days', now() - interval '2 days', now() - interval '1 day'),
  ('90000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000012', '30000000-0000-4000-8000-000000000005',
   'OFFICIAL_CHANGE', '50000000-0000-4000-8000-000000000003', 'Rock no Parque foi cancelado',
   'Consulta as condições de reembolso.', 'demo-official-change-bruno-rock',
   now() - interval '6 hours', now() - interval '6 hours', NULL),
  ('90000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000011', '30000000-0000-4000-8000-000000000002',
   'OPERATIONAL_ALERT', '60000000-0000-4000-8000-000000000001', 'Entrada Sul fechada',
   'Usem a Entrada Norte, na Rua do Almada.', 'demo-alert-ana-avenida',
   now() - interval '12 minutes', now() - interval '12 minutes', NULL),
  ('90000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000012', '30000000-0000-4000-8000-000000000002',
   'OPERATIONAL_ALERT', '60000000-0000-4000-8000-000000000001', 'Entrada Sul fechada',
   'Usem a Entrada Norte, na Rua do Almada.', 'demo-alert-bruno-avenida',
   now() - interval '12 minutes', now() - interval '12 minutes', NULL),
  ('90000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000013', '30000000-0000-4000-8000-000000000004',
   'SEVERE_WEATHER', NULL, 'Previsão de trovoada forte',
   'O Arraial de São João pode ser afetado.', 'demo-weather-carla-arraial',
   now() - interval '1 hour', NULL, NULL),
  ('90000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000012', '30000000-0000-4000-8000-000000000002',
   'RECOGNITION', NULL, 'O teu reporte fez a diferença',
   'Ajudou a organização a abrir uma segunda entrada.', 'demo-recognition-bruno-avenida',
   now() - interval '5 minutes', now() - interval '5 minutes', NULL)
ON CONFLICT DO NOTHING;
