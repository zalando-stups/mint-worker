(ns org.zalando.stups.mint.worker.job.run
  (:require [org.zalando.stups.friboo.system.cron :refer [def-cron-component job]]
            [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.friboo.config :as config]
            [org.zalando.stups.mint.worker.job.sync-app :refer [sync-app]]
            [org.zalando.stups.mint.worker.external.storage :as storage]
            [org.zalando.stups.mint.worker.external.apps :as apps]
            [overtone.at-at :refer [every]]
            [clj-time.core :as time]))

(def default-configuration
  {:jobs-cpu-count        1
   :jobs-every-ms         10000
   :jobs-initial-delay-ms 1000
   :jobs-max-s3-errors    10})

; used to track rate limiting of service user api
(def rate-limited-until (atom (time/epoch)))

(defn run-sync
  "Creates and deletes applications, rotates and distributes their credentials."
  [configuration tokens]
  (try
    (when (time/after? (time/now)
                       @rate-limited-until)
      (let [storage-url (config/require-config configuration :mint-storage-url)
            kio-url (config/require-config configuration :kio-url)]
        (log/debug "Starting new synchronisation run with %s..." configuration)

        (let [mint-apps (storage/list-apps storage-url tokens)
              kio-apps (apps/list-apps kio-url tokens)]
          (log/debug "Found apps: %s ..." mint-apps)
          (doseq [mint-app mint-apps]
            (try
              (sync-app configuration
                        mint-app
                        ; searching kio-app here
                        (first (filter #(= (:id %)
                                           (:id mint-app))
                                       kio-apps))
                        tokens)
              (storage/update-status storage-url (:id mint-app)
                                                 {:has_problems false
                                                  :s3_errors 0
                                                  :message ""}
                                                 tokens)
              (catch Exception e
                (when (= 429 (:status  (ex-data e)))
                  ; bubble up if we are rate limited
                  (throw e))
                (storage/update-status storage-url (:id mint-app)
                                                   {:has_problems true
                                                    :s3_errors (when (= "S3Exception" (:type (ex-data e)))
                                                                 (inc (:s3_errors mint-app)))
                                                    :message (str e)}
                                                   tokens)
                (log/warn "Could not synchronize app %s because %s." (:id mint-app) (str e))))))))
    (catch Throwable e
      (if (= 429 (:status (ex-data e)))
        (do
          (log/warn "We got rate limited; pausing activities for the next 90 seconds.")
          (reset! rate-limited-until (time/plus (time/now) (time/seconds 90))))
        (log/error e "Could not synchronize apps because %s." (str e))))))

(def-cron-component
  Jobs [tokens]

  (let [{:keys [every-ms initial-delay-ms]} configuration]

    (every every-ms (job run-sync configuration tokens) pool :initial-delay initial-delay-ms :desc "synchronisation")))
