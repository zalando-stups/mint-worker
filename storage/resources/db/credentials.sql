-- name: read-credentials
SELECT application_id,
       last_password_rotation,
       last_secret_rotation
  FROM credential;

-- name: read-credential
SELECT application_id,
       last_password_rotation,
       last_secret_rotation,
       application_username,
       client_id
  FROM credential
 WHERE application_id = :application_id;

-- name: read-credential-sensitive
SELECT application_id,
       last_password_rotation,
       last_secret_rotation,
       application_username,
       application_password,
       client_id,
       client_secret
  FROM credential
 WHERE application_id = :application_id;

-- name: create-credential!
INSERT INTO credential
       (application_id, application_username, client_id)
VALUES (:application_id, :application_username, :client_id);

-- name: delete-credential!
DELETE FROM credential
 WHERE application_id = :application_id;

-- name: update-application-password!
UPDATE credential
   SET application_password = :application_password,
       last_password_rotation = to_timestamp(:last_password_rotation)
 WHERE application_id = :application_id;

-- name: update-client-secret!
UPDATE credential
   SET client_id = :client_id,
       client_secret = :client_secret,
       last_secret_rotation = to_timestamp(:last_secret_rotation)
 WHERE application_id = :application_id;
