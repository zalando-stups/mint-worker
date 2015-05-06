CREATE SCHEMA zm_data;
SET search_path TO zm_data;

CREATE TABLE application (
  a_id                     TEXT        NOT NULL PRIMARY KEY,

  a_redirect_url           TEXT,

  a_username               TEXT        NOT NULL,
  a_client_id              TEXT,

  a_last_password_rotation TIMESTAMPTZ,
  a_last_client_rotation   TIMESTAMPTZ,
  a_last_modified          TIMESTAMPTZ NOT NULL,
  a_last_synced            TIMESTAMPTZ,

  a_has_problems           BOOLEAN     NOT NULL DEFAULT FALSE,
  a_s3_buckets             TEXT        NOT NULL
);

COMMENT ON COLUMN application.a_s3_buckets IS 'comma-separated list';

CREATE TABLE scope (
  s_application_id   TEXT NOT NULL REFERENCES application (a_id) ON DELETE CASCADE,
  s_resource_type_id TEXT NOT NULL,
  s_scope_id         TEXT NOT NULL,

  PRIMARY KEY (s_application_id, s_resource_type_id, s_scope_id)
);

CREATE INDEX scope_application_id_idx ON scope (s_application_id);