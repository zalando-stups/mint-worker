-- name: read-applications
SELECT ap_id,
       ap_last_password_rotation,
       ap_last_client_rotation,
       ap_has_problems
  FROM application;

-- name: create-application!
INSERT INTO application
            (ap_id, ap_redirect_url, ap_username)
     VALUES (:application_id, :redirect_url, :username);

-- name: read-application
SELECT ap_id,
       ap_redirect_url,
       ap_username,
       ap_client_id,
       ap_last_password_rotation,
       ap_last_client_rotation,
       ap_has_problems
  FROM application
 WHERE ap_id = :application_id;

-- name: update-application!
UPDATE application
   SET ap_redirect_url = COALESCE(:redirect_url, ap_redirect_url)
 WHERE ap_id = :application_id;

-- name: update-application-status!
UPDATE application
   SET ap_client_id = COALESCE(:client_id, ap_client_id),
       ap_last_password_rotation = COALESCE(:last_password_rotation, ap_last_password_rotation),
       ap_last_client_rotation = COALESCE(:last_client_rotation, ap_last_client_rotation),
       ap_has_problems = COALESCE(:has_problems, ap_has_problems)
 WHERE ap_id = :application_id;

-- name: delete-application!
DELETE FROM application WHERE ap_id = :application_id;


-- name: create-account!
INSERT INTO account
            (ac_application_id, ac_id, ac_type)
     VALUES (:application_id, :account_id, :account_type);

-- name: read-accounts
SELECT ac_id, ac_type
  FROM account
 WHERE ac_application_id = :application_id;

-- name: delete-account!
DELETE FROM account
 WHERE ac_application_id = :application_id
   AND ac_id = :account_id
   AND ac_type = :account_type;
