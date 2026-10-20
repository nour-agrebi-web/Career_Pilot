-- CareerPilot : schéma de la base (équivalent de la migration Alembic 39d589a1a859)
--
-- À utiliser seulement si "alembic upgrade head" ne fonctionne pas.
-- La dernière instruction marque la base comme migrée : Alembic pourra reprendre ensuite.
-- Lancer depuis le dossier backend :
--     psql -U careerpilot -h localhost -d careerpilot_db -f seeds/schema.sql

SET client_encoding = 'UTF8';

BEGIN;

CREATE TABLE alembic_version (
    version_num VARCHAR(32) NOT NULL,
    CONSTRAINT alembic_version_pkc PRIMARY KEY (version_num)
);

-- Running upgrade  -> 39d589a1a859

CREATE TYPE role AS ENUM ('CANDIDATE', 'ADMIN');

CREATE TABLE users (
    id UUID NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    language VARCHAR(5) NOT NULL,
    consent BOOLEAN NOT NULL,
    consent_at TIMESTAMP WITH TIME ZONE,
    role role NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX ix_users_email ON users (email);

CREATE TABLE refresh_tokens (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked BOOLEAN NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY(user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX ix_refresh_tokens_user_id ON refresh_tokens (user_id);

INSERT INTO alembic_version (version_num) VALUES ('39d589a1a859');

COMMIT;

