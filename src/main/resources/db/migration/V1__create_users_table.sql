-- V1: users table
--
-- The core identity entity. Owns books, writes reviews, lends and borrows.
-- Schema is owned by Flyway (jpa.ddl-auto = none), so this file is the source
-- of truth for the table's shape.
--
-- Decisions locked in (see session notes):
--   * id            UUID primary key, generated app-side (UUIDv7) — no DB default
--   * username      unique public handle
--   * email         unique, used for auth/contact
--   * password      stores a HASH, never plaintext (hashing is Phase 5). Size for bcrypt.
--   * role          VARCHAR + CHECK constraint: BORROWER | LENDER | ADMIN
--   * created_at /  audit timestamps; store UTC (consider TIMESTAMPTZ)
--     updated_at

-- TODO(human): write the CREATE TABLE users (...) statement using the decisions above.

CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(255) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL ,
    password TEXT NOT NULL ,
    role TEXT NOT NULL CONSTRAINT check_valid_role CHECK(role IN('BORROWER', 'LENDER', 'ADMIN')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);