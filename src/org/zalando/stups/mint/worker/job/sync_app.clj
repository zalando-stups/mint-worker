(ns org.zalando.stups.mint.worker.job.sync-app
  (:require [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.friboo.config :as config]
            [org.zalando.stups.mint.worker.external.apps :as apps]
            [org.zalando.stups.mint.worker.external.storage :as storage]
            [org.zalando.stups.mint.worker.external.s3 :as s3]
            [org.zalando.stups.mint.worker.job.sync-client :refer [sync-client]]
            [org.zalando.stups.mint.worker.job.sync-user :refer [sync-user]]
            [org.zalando.stups.mint.worker.job.sync-password :refer [sync-password]]))

(defn sync-app
  "Syncs the application with the given app-id."
  [configuration app tokens]
  (let [app-id (:id app)
        s3-errors (:s3_errors app)
        storage-url (config/require-config configuration :mint-storage-url)
        kio-url (config/require-config configuration :kio-url)
        max-errors (:max-s3-errors configuration)]
    (log/debug "============================== %s ==============================" app-id)
    (log/debug "Start syncing app %s..." app-id)
    (if-not (> s3-errors
               max-errors)
            (let [app        (storage/get-app storage-url app-id tokens)
                  unwritable (doall (remove #(s3/writable? % app-id) (:s3_buckets app)))]
              (if (seq unwritable)
                  (do
                    (log/debug "Skipping sync for app %s because there are unwritable S3 buckets: %s" app-id unwritable)
                    (storage/update-status storage-url app-id
                                                       {:has_problems true
                                                        :s3_errors (inc s3-errors)
                                                        :message (str "Unwritable S3 buckets: " (str unwritable))}
                                                       tokens))
                  ; TODO handle 404 from kio for app
                  (let [kio-app (apps/get-app kio-url app-id tokens)
                        app (sync-user app kio-app configuration tokens)]
                    (log/debug "App has mint configuration %s" app)
                    (log/debug "App has kio configuration %s" kio-app)
                    (if (:active kio-app)
                      (do
                        (sync-password app configuration tokens)
                        (if (:is_client_confidential app)
                          (sync-client app configuration tokens)
                          (log/debug "Skipping client rotation for non-confidential client %s" app-id)))
                      (log/debug "Skipping password and client rotation for inactive app %s" app-id))
                    (log/debug "Synced app %s." app-id))))
            ; else
            (log/debug "Skipping sync for app %s because could not write to S3 repeatedly" app-id))))