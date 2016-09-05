(ns org.zalando.stups.mint.worker.job.sync-client
  (:require [clj-time.core :as time]
            [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.friboo.config :as config]
            [org.zalando.stups.mint.worker.job.common :as c]
            [org.zalando.stups.mint.worker.external.services :as services]
            [org.zalando.stups.mint.worker.external.storage :as storage]
            [org.zalando.stups.mint.worker.external.bucket_storage :refer [save-client]]
            [clojure.string :as str]))

(defn sync-client
  "If neccessary, creates and syncs new client credentials for the given app"
  [{:keys [id client_id username s3_buckets last_client_rotation]} configuration tokens]
  {:pre [(not (str/blank? id))
         (seq s3_buckets)
         (not (str/blank? username))]}
  (let [service-user-url (config/require-config configuration :service-user-url)
        storage-url (config/require-config configuration :mint-storage-url)]
    (if (or (nil? last_client_rotation)
            (time/after? (time/now)
                         (time/plus (c/parse-date-time last_client_rotation)
                                    (time/months 1))))
        (do
          ; Step 1: generate password
          (log/debug "Acquiring a new client for app %s..." id)
          (let [generate-client-response (services/generate-new-client service-user-url username client_id tokens)
                new-client-id (:client_id generate-client-response)
                transaction-id (:txid generate-client-response)
                client-secret (:client_secret generate-client-response)
                params {:app-id id, :client-id new-client-id, :client-secret client-secret}]
            ; Step 2: distribute it
            (log/debug "Saving the new client for %s to buckets: %s..." id s3_buckets)
            (if-let [error (c/has-error (c/busy-map #(save-client (assoc params :bucket-name %))
                                                    s3_buckets))]
              (do
                (log/debug "Could not save client to bucket: %s" (str error))
                (throw error))
              (do
                ; Step 3: if successful distributed, commit it
                (log/debug "Committing new secret for app %s with transaction %s..." id transaction-id)
                (services/commit-client service-user-url username transaction-id tokens)

                ; Step 4: store last rotation time (now)
                (log/debug "Saving last client rotation status for app %s..." id)
                (storage/update-status storage-url id
                                                   {:last_client_rotation (c/format-date-time (time/now))
                                                    :client_id            new-client-id}
                                                   tokens)
                (log/info "Successfully rotated client for app %s" id)))))

    ; else
    (log/debug "Client for app %s is still valid. Skip client rotation." id))))
