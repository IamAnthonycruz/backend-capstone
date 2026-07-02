-- V9: refresh_tokens table
--
-- Refresh tokens are the STATEFUL half of auth (access tokens stay stateless/signed).
-- One row per issued refresh token so each can be revoked individually — the whole
-- point of storing them (see session notes: stateless == all-or-nothing revocation).
--
-- Design (derived together):
--   * id          UUID PK, app-generated
--   * user_id     UUID FK -> users(id). ON DELETE CASCADE: a deleted user's tokens are
--                 meaningless, so they go with them.
--   * token_hash  the token is stored HASHED, never raw (same reasoning as passwords).
--                 UNIQUE both as a data-integrity backstop and to index lookups by hash.
--                 NOTE: lookup-by-hash only works if the hash is DETERMINISTIC (e.g.
--                 SHA-256), not salted like BCrypt — that's a logic decision for the service.
--   * family_id   groups one login's rotation lineage. Reuse detection revokes a whole
--                 family without touching the user's other sessions.
--   * expires_at  long TTL (e.g. 7d); the absolute cutoff even if never revoked.
--   * revoked     used-or-killed flag. A rotated (spent) token is revoked, not deleted,
--                 so a later replay is still detectable.
--   * created_at  audit.

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    token_hash TEXT NOT NULL UNIQUE,
    family_id UUID NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_refresh_token_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);

-- Reuse detection revokes by family; index it.
CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens (family_id);
