-- CareerPilot : données de test pour le développement (base careerpilot_db)
--
-- Prérequis : les tables existent déjà (lancer d'abord "alembic upgrade head" dans backend/).
-- Lancer depuis PowerShell, dans le dossier backend :
--     psql -U careerpilot -h localhost -d careerpilot_db -f seeds/seed_dev.sql
--
-- Tous les comptes ont le même mot de passe : motdepasse123
-- (les mots de passe sont stockés hachés en Argon2, jamais en clair).
-- Le script peut être relancé : les comptes déjà présents sont ignorés.

SET client_encoding = 'UTF8';

BEGIN;

INSERT INTO users (id, email, password_hash, language, consent, consent_at, role, created_at) VALUES
  ('11111111-1111-4111-8111-111111111111', 'admin@careerpilot.tn',
   '$argon2id$v=19$m=65536,t=3,p=4$ZoHFg1IoEk0m1owAIZGKDA$ojk5S7izIZJtfT1O5To6h7ALLs9OADzgtiyHDGfwQeM',
   'fr', true, now(), 'ADMIN', now()),
  ('22222222-2222-4222-8222-222222222222', 'nour.agrebi@etudiant.tn',
   '$argon2id$v=19$m=65536,t=3,p=4$L1L8BKmA8AuUHbltXXU1oA$T8/iUXV5qw84VuVwx/GFMR6QYDt2M04Lzdrslz9XRd8',
   'fr', true, now(), 'CANDIDATE', now()),
  ('33333333-3333-4333-8333-333333333333', 'amine.hayouni@etudiant.tn',
   '$argon2id$v=19$m=65536,t=3,p=4$NxmZctwdntgf965pLMwQDg$NViaCcAhqUVKS1xAfxFvkRrEbjgpEmlbAZdAaeUwWAU',
   'fr', true, now(), 'CANDIDATE', now()),
  ('44444444-4444-4444-8444-444444444444', 'dhia.battikh@etudiant.tn',
   '$argon2id$v=19$m=65536,t=3,p=4$S5FDZLs0DOOLw7PLEPHK2w$XEKG4pa+ea+XLkW+PARWpEs7D/+qbS+PTOplHOs2Iio',
   'ar', true, now(), 'CANDIDATE', now()),
  ('55555555-5555-4555-8555-555555555555', 'sarah.martin@example.fr',
   '$argon2id$v=19$m=65536,t=3,p=4$C8c5yrxbvUsJqIyDUNSTTA$UJ4CLYaz3k1gqF4KSAYF/QbyA/BcTUgY8lsw9BY1l8E',
   'en', true, now(), 'CANDIDATE', now())
ON CONFLICT (email) DO NOTHING;

COMMIT;

-- Vérification
SELECT email, role, language, consent FROM users ORDER BY role, email;
