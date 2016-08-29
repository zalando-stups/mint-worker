(ns org.zalando.stups.mint.worker.job.sync-app
  (:require [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.friboo.config :as config]
            [org.zalando.stups.mint.worker.external.storage :as storage]
            [org.zalando.stups.mint.worker.external.bucket_storage :refer [writable?
                                                                           StorageException]]
            [org.zalando.stups.mint.worker.job.sync-client :refer [sync-client]]
            [org.zalando.stups.mint.worker.job.sync-user :refer [sync-user]]
            [org.zalando.stups.mint.worker.job.sync-password :refer [sync-password]]
            [clojure.string :as str]))

(defn sync-app
  "Syncs the application with the given app-id."
  [backend {:keys [max-s3-errors] :as configuration} {:keys [id s3_errors]} kio-app tokens]
  {:pre [(not (str/blank? id))]}
  (let [storage-url (config/require-config configuration :mint-storage-url)
        kio-url (config/require-config configuration :kio-url)]
    (log/debug "============================== %s ==============================" id)
    (log/debug "Start syncing app %s..." id)
    (if-not (> s3_errors
               max-s3-errors)
            (let [app        (storage/get-app storage-url id tokens)
                  unwritable (doall (remove #(writable? backend % id) (:s3_buckets app)))]
              (if (seq unwritable)
                  ; unwritable buckets! skip sync.
                  (do
                    (log/debug "Skipping sync for app %s because there are unwritable buckets: %s" id unwritable)
                    ; now throw exception so that it will be handled in run.clj
                    (throw (StorageException (str "Unwritable buckets: "
                                                (pr-str unwritable))
                                           {:s3_buckets unwritable})))
                  ; writable buckets, presumably. do sync.
                  ; TODO handle nil for kio-app
                  (let [app (merge app (sync-user backend app kio-app configuration tokens))]
                    (log/debug "App has mint configuration %s" app)
                    (log/debug "App has kio configuration %s" kio-app)
                    (if (:active kio-app)
                      (do
                        (sync-password backend app configuration tokens)
                        (if (:is_client_confidential app)
                          (sync-client backend app configuration tokens)
                          (log/debug "Skipping client rotation for non-confidential client %s" id)))
                      (log/debug "Skipping password and client rotation for inactive app %s" id))
                    (log/info "Synced app %s." id))))
            ; else
            (log/debug "Skipping sync for app %s because could not write to bucket storage repeatedly" id))))
