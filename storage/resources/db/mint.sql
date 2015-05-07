-- name: read-applications
SELECT a_id,
       a_last_password_rotation,
       a_last_client_rotation,
       a_last_modified,
       a_last_synced,
       a_has_problems
  FROM zm_data.application
 ORDER BY a_id;

-- name: filter-applications
SELECT DISTINCT
       a_id,
       a_last_password_rotation,
       a_last_client_rotation,
       a_last_modified,
       a_last_synced,
       a_has_problems
  FROM zm_data.application
  JOIN zm_data.scope ON s_application_id = a_id
 WHERE s_resource_type_id = COALESCE(:resource_type_id, s_resource_type_id)
   AND s_scope_id = COALESCE(:scope_id, s_scope_id)
 ORDER BY a_id;

-- name: create-application!
INSERT INTO zm_data.application
            (a_id, a_redirect_url, a_username, a_s3_buckets, a_last_modified)
     VALUES (:application_id, :redirect_url, :username, :s3_buckets, now());

-- name: read-application
SELECT a_id,
       a_redirect_url,
       a_username,
       a_client_id,
       a_last_password_rotation,
       a_last_client_rotation,
       a_last_modified,
       a_last_synced,
       a_has_problems,
       a_s3_buckets
  FROM zm_data.application
 WHERE a_id = :application_id;

-- name: update-application!
UPDATE zm_data.application
   SET a_redirect_url = COALESCE(:redirect_url, a_redirect_url),
       a_s3_buckets = COALESCE(:s3_buckets, a_s3_buckets),
       a_last_modified = now()
 WHERE a_id = :application_id;

-- name: update-application-status!
UPDATE zm_data.application
   SET a_client_id = COALESCE(:client_id, a_client_id),
       a_last_password_rotation = COALESCE(:last_password_rotation, a_last_password_rotation),
       a_last_client_rotation = COALESCE(:last_client_rotation, a_last_client_rotation),
       a_last_synced = COALESCE(:last_synced, a_last_synced),
       a_has_problems = COALESCE(:has_problems, a_has_problems)
 WHERE a_id = :application_id;

-- name: delete-application!
DELETE FROM zm_data.application WHERE a_id = :application_id;

-- name: read-scopes
SELECT s_resource_type_id,
       s_scope_id
  FROM zm_data.scope
 WHERE s_application_id = :application_id
 ORDER BY s_resource_type_id, s_scope_id;

-- name: create-scope!
INSERT INTO zm_data.scope (s_application_id, s_resource_type_id, s_scope_id)
    VALUES (:application_id, :resource_type_id, :scope_id);

-- name: delete-scope!
DELETE FROM zm_data.scope
 WHERE s_application_id = :application_id
   AND s_resource_type_id = :resource_type_id
   AND s_scope_id = :scope_id
