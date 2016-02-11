(ns org.zalando.stups.mint.worker.job.sync-password
  (:require [clj-time.core :as time]
            [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.friboo.config :as config]
            [org.zalando.stups.mint.worker.job.common :as c]
            [org.zalando.stups.mint.worker.external.services :as services]
            [org.zalando.stups.mint.worker.external.storage :as storage]
            [org.zalando.stups.mint.worker.external.s3 :as s3]
            [clojure.string :as str]))

(defn sync-password
  "If neccessary, creates and syncs a new password for the given app."
  [{:keys [id username last_password_rotation s3_buckets]} configuration tokens]
  {:pre [(not (str/blank? id))
         (seq s3_buckets)
         (not (str/blank? username))]}
  (let [storage-url (config/require-config configuration :mint-storage-url)
        shadow-user-url (config/require-config configuration :shadow-service-user-url)
        service-user-url (config/require-config configuration :service-user-url)]
    (if (or (nil? last_password_rotation)
            (time/after? (time/now)
                         (time/plus (c/parse-date-time last_password_rotation)
                                    (time/hours 2))))
      (do
        ; Step 1: Generate password in primary
        (log/debug "Acquiring new password for %s in primary..." username)
        (let [{:keys [password txid]} (services/generate-new-password service-user-url
                                                                      username
                                                                      nil
                                                                      tokens)]
          ; Step 2: Set password in shadow
          (log/debug "Setting new password for %s in shadow..." username)
          (services/generate-new-password shadow-user-url
                                          username
                                          {:txid txid
                                           :password password}
                                          tokens)
          ; Step 3: distribute it
          (log/debug "Saving the new password for %s to S3 buckets: %s..." id s3_buckets)
          (if-let [error (c/has-error (c/busy-map #(s3/save-user % id username password)
                                                  s3_buckets))]
            (do
              (log/debug "Could not save password to bucket: %s" (str error))
              (throw error))
            (do
              ; Step 4: if successful distributed, commit it
              (log/debug "Committing new password for app %s with transaction %s in primary..." id txid)
              (services/commit-password service-user-url username
                                                         txid
                                                         tokens)
              (log/debug "Committing new password for app %s with transaction %s in shadow..." id txid)
              (services/commit-password shadow-user-url
                                        username
                                        txid
                                        tokens)

              ; Step 5: store last rotation time (now)
              (log/debug "Saving last password rotation status for app %s..." id)
              (storage/update-status storage-url id
                                                 {:last_password_rotation (c/format-date-time (time/now))}
                                                 tokens)

              (log/info "Successfully rotated password for app %s" id)))))

      ; else
      (log/debug "Password for app %s is still valid. Skip password rotation." id))))