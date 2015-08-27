(ns org.zalando.stups.mint.worker.job.sync-client
  (:require [clj-time.core :as time]
            [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.friboo.config :as config]
            [org.zalando.stups.mint.worker.job.common :as c]
            [org.zalando.stups.mint.worker.external.services :as services]
            [org.zalando.stups.mint.worker.external.storage :as storage]
            [org.zalando.stups.mint.worker.external.s3 :as s3]))

(defn sync-client
  "If neccessary, creates and syncs new client credentials for the given app"
  [app configuration tokens]
  (let [service-user-url (config/require-config configuration :service-user-url)
        storage-url (config/require-config configuration :mint-storage-url)
        app-id (:id app)
        client-id (:client_id app)
        username (:username app)
        bucket-names (:s3_buckets app)]
    (if (or (nil? (:last_client_rotation app))
            (time/after? (time/now)
                         (time/plus (c/parse-date-time (:last_client_rotation app))
                                    (time/months 1))))
        (do
          ; Step 1: generate password
          (log/info "Acquiring a new client for app %s..." app-id)
          (let [generate-client-response (services/generate-new-client service-user-url username client-id tokens)
                client-id (:client_id generate-client-response)
                transaction-id (:txid generate-client-response)
                client-secret (:client_secret generate-client-response)]
            ; Step 2: distribute it
            (log/info "Saving the new client for %s to S3 buckets: %s..." app-id bucket-names)
            (if-let [error (c/has-error (c/busy-map #(s3/save-client % app-id client-id client-secret)
                                                    bucket-names))]
              (do
                (log/debug "Could not save client to bucket: %s" (str error))
                (throw error))
              (do
                ; Step 3: if successful distributed, commit it
                (log/debug "Committing new secret for app %s with transaction %s..." app-id transaction-id)
                (services/commit-client service-user-url username transaction-id tokens)

                ; Step 4: store last rotation time (now)
                (log/debug "Saving last client rotation status for app %s..." app-id)
                (storage/update-status storage-url app-id
                                                   {:last_client_rotation (c/format-date-time (time/now))
                                                    :client_id            client-id}
                                                   tokens)
                (log/info "Successfully rotated client for app %s" app-id)))))

    ; else
    (log/debug "Client for app %s is still valid. Skip client rotation." app-id))))