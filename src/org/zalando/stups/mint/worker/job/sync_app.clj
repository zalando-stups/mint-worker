(ns org.zalando.stups.mint.worker.job.sync-app
  (:require [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.friboo.config :as config]
            [org.zalando.stups.mint.worker.external.apps :as apps]
            [org.zalando.stups.mint.worker.external.storage :as storage]
            [org.zalando.stups.mint.worker.external.s3 :as s3]
            [org.zalando.stups.mint.worker.job.sync-client :refer [sync-client]]
            [org.zalando.stups.mint.worker.job.sync-user :refer [sync-user]]
            [org.zalando.stups.mint.worker.job.sync-password :refer [sync-password]]
            [clojure.string :as str]))

(defn sync-app
  "Syncs the application with the given app-id."
  [{:keys [max-s3-errors] :as configuration} {:keys [id s3_errors]} tokens]
  {:pre [(not (str/blank? id))]}
  (let [storage-url (config/require-config configuration :mint-storage-url)
        kio-url (config/require-config configuration :kio-url)]
    (log/debug "============================== %s ==============================" id)
    (log/debug "Start syncing app %s..." id)
    (if-not (> s3_errors
               max-s3-errors)
            (let [app        (storage/get-app storage-url id tokens)
                  unwritable (doall (remove #(s3/writable? % id) (:s3_buckets app)))]
              (if (seq unwritable)
                  (do
                    (log/debug "Skipping sync for app %s because there are unwritable S3 buckets: %s" id unwritable)
                    (storage/update-status storage-url id
                                                       {:has_problems true
                                                        :s3_errors (inc s3_errors)
                                                        :message (str "Unwritable S3 buckets: " (str unwritable))}
                                                       tokens))
                  ; TODO handle 404 from kio for app
                  (let [kio-app (apps/get-app kio-url id tokens)
                        app (merge app (sync-user app kio-app configuration tokens))]
                    (log/debug "App has mint configuration %s" app)
                    (log/debug "App has kio configuration %s" kio-app)
                    (if (:active kio-app)
                      (do
                        (sync-password app configuration tokens)
                        (if (:is_client_confidential app)
                          (sync-client app configuration tokens)
                          (log/debug "Skipping client rotation for non-confidential client %s" id)))
                      (log/debug "Skipping password and client rotation for inactive app %s" id))
                    (log/debug "Synced app %s." id))))
            ; else
            (log/debug "Skipping sync for app %s because could not write to S3 repeatedly" id))))
