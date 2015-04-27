(ns org.zalando.stups.mint.worker.job
  (:require [org.zalando.stups.friboo.system.cron :refer [def-cron-component]]
            [org.zalando.stups.friboo.log :as log]
            [overtone.at-at :refer [every]]
            [org.zalando.stups.mint.worker.external.storage :as storage]))

(def default-configuration
  {:jobs-cpu-count        1
   :jobs-every-ms         10000
   :jobs-initial-delay-ms 1000})

(defn sync-app [storaga-url app-id]
  (let [app (storage/get-app-details storaga-url app-id)]
    ; sync
    ))

(defn run-sync
  "Creates and deletes applications, rotates and distributes their credentials."
  [configuration]
  (let [{:keys [storage-url]} configuration]
    (log/info "Starting new synchronisation run with %s..." configuration)
    (try
      (let [apps (storage/get-managed-apps storage-url)]
        (doseq [app apps]
          (try
            (sync-app storage-url (:id app))
            (catch Exception e
              (log/warn e "Could not synchronize app %s because %s." app (str e)))))
        ; TODO delete users that are not managed apps anymore
        )
      (catch Exception e
        (log/error e "Could not synchronize apps because %s." (str e))))))

(def-cron-component
  Jobs []

  (let [{:keys [every-ms initial-delay-ms]} configuration]

    (every every-ms (partial run-sync configuration) pool :initial-delay initial-delay-ms :desc "synchronisation")))
