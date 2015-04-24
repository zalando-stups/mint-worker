CREATE TABLE application (
  ap_id TEXT NOT NULL,

  ap_redirect_url TEXT NOT NULL,

  ap_username TEXT NOT NULL,
  ap_client_id TEXT,

  ap_last_password_rotation  TIMESTAMPTZ,
  ap_last_client_rotation    TIMESTAMPTZ,

  ap_has_problems BOOLEAN NOT NULL DEFAULT FALSE,

  PRIMARY KEY (ap_id)
);

CREATE TABLE account (
  ac_application_id TEXT NOT NULL,

  ac_id TEXT NOT NULL,
  ac_type TEXT NOT NULL,

  PRIMARY KEY (ac_id, ac_type, ac_application_id),
  FOREIGN KEY (ac_application_id) REFERENCES application (ap_id) ON DELETE CASCADE
);
