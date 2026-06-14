CREATE SCHEMA IF NOT EXISTS app;

CREATE TABLE app.todos (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     TEXT        NOT NULL,
    title       TEXT        NOT NULL,
    completed_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
