-- base entity: application
CREATE TABLE credential (
-- the official application ID, like 'kio' or 'pierone'
  application_id          TEXT NOT NULL,

-- status of rotation
  last_password_rotation  TIMESTAMP,
  last_secret_rotation    TIMESTAMP,

-- service user credentials
  application_username    TEXT,
  application_password    TEXT,

-- oauth 2.0 secrets
  client_id               TEXT,
  client_secret           TEXT,

  PRIMARY KEY (application_id)
);
