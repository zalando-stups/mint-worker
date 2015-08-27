(ns org.zalando.stups.mint.worker.job.sync-password
  (:require [clj-time.core :as time]
            [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.friboo.config :as config]
            [org.zalando.stups.mint.worker.job.common :as c]
            [org.zalando.stups.mint.worker.external.services :as services]
            [org.zalando.stups.mint.worker.external.storage :as storage]
            [org.zalando.stups.mint.worker.external.s3 :as s3]))

(defn sync-password
  "If neccessary, creates and syncs a new password for the given app."
  [app configuration tokens]
  (let [storage-url (config/require-config configuration :mint-storage-url)
        service-user-url (config/require-config configuration :service-user-url)
        username (:username app)
        app-id (:id app)]
    (if (or (nil? (:last_password_rotation app))
            (time/after? (time/now)
                         (time/plus (c/parse-date-time (:last_password_rotation app))
                                    (time/hours 2))))
      (do
        ; Step 1: generate password
        (log/info "Acquiring new password for %s..." username)
        (let [{:keys [password txid]} (services/generate-new-password service-user-url username tokens)
              bucket-names (:s3_buckets app)]
          ; Step 2: distribute it
          (log/info "Saving the new password for %s to S3 buckets: %s..." app-id bucket-names)
          (if-let [error (c/has-error (c/busy-map #(s3/save-user % app-id username password)
                                              bucket-names))]
            (do
              (log/debug "Could not save password to bucket: %s" (str error))
              (throw error))
            (do
              ; Step 3: if successful distributed, commit it
              (log/debug "Committing new password for app %s with transaction %s..." app-id txid)
              (services/commit-password service-user-url username
                                                         txid
                                                         tokens)

              ; Step 4: store last rotation time (now)
              (log/debug "Saving last password rotation status for app %s..." app-id)
              (storage/update-status storage-url app-id
                                                 {:last_password_rotation (c/format-date-time (time/now))}
                                                 tokens)

              (log/info "Successfully rotated password for app %s" app-id)))))

      ; else
      (log/debug "Password for app %s is still valid. Skip password rotation." app-id))))