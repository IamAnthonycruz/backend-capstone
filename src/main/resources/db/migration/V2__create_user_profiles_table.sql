-- V2: user_profiles table
--
-- One-to-one with users. Holds descriptive details about a person that aren't
-- needed to authenticate them (display name, bio, avatar, location, ...).
--
-- Decisions locked in:
--   * id        UUID primary key, app-generated (same scheme as users)
--   * user_id   UUID FK -> users(id), NOT NULL + UNIQUE (UNIQUE = the 1-to-1)
--   * ON DELETE behavior: TODO(human) decide CASCADE vs RESTRICT vs default
--   * profile fields: your call (display_name, bio, avatar_url, location, ...)
--   * created_at / updated_at: same TIMESTAMPTZ + DEFAULT NOW() pattern as V1

-- TODO(human): write the CREATE TABLE user_profiles (...) statement.

CREATE TABLE user_profiles(
    id UUID PRIMARY KEY NOT NULL,
    user_id UUID NOT NULL UNIQUE,
    display_name TEXT NOT NULL,
    bio TEXT,
    location TEXT,
    profile_picture_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_user
        FOREIGN KEY(user_id)
        REFERENCES users(id) ON DELETE CASCADE

);



