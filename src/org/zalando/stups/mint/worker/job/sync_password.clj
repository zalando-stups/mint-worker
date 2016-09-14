(ns org.zalando.stups.mint.worker.job.sync-password
  (:require [clj-time.core :as time]
            [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.friboo.config :as config]
            [org.zalando.stups.mint.worker.job.common :as c]
            [org.zalando.stups.mint.worker.external.services :as services]
            [org.zalando.stups.mint.worker.external.storage :as storage]
            [org.zalando.stups.mint.worker.external.bucket_storage :refer [save-user]]
            [clojure.string :as str]))

(defn sync-password
  "If neccessary, creates and syncs a new password for the given app."
  [{:keys [id username last_password_rotation s3_buckets]} configuration tokens]
  {:pre [(not (str/blank? id))
         (seq s3_buckets)
         (not (str/blank? username))]}
  (let [storage-url (config/require-config configuration :mint-storage-url)
        service-user-url (config/require-config configuration :service-user-url)]
    (if (or (nil? last_password_rotation)
            (time/after? (time/now)
                         (time/plus (c/parse-date-time last_password_rotation)
                                    (time/hours 2))))
      (do
        ; Step 1: generate password
        (log/debug "Acquiring new password for %s..." username)
        (let [{:keys [password txid]} (services/generate-new-password service-user-url username tokens)]
          ; Step 2: distribute it
          (log/debug "Saving the new password for %s to buckets: %s..." id s3_buckets)
          (if-let [error (c/has-error (c/busy-map #(save-user % id username password configuration tokens)
                                                  s3_buckets))]
            (do
              (log/debug "Could not save password to bucket: %s" (str error))
              (throw error))
            (do
              ; Step 3: if successful distributed, commit it
              (log/debug "Committing new password for app %s with transaction %s..." id txid)
              (services/commit-password service-user-url username
                                                         txid
                                                         tokens)

              ; Step 4: store last rotation time (now)
              (log/debug "Saving last password rotation status for app %s..." id)
              (storage/update-status storage-url id
                                                 {:last_password_rotation (c/format-date-time (time/now))}
                                                 tokens)

              (log/info "Successfully rotated password for app %s" id)))))

      ; else
      (log/debug "Password for app %s is still valid. Skip password rotation." id))))
