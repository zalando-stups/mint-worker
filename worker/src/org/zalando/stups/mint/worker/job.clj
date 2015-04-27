(ns org.zalando.stups.mint.worker.job
  (:require [org.zalando.stups.friboo.system.cron :refer [def-cron-component]]
            [org.zalando.stups.friboo.log :as log]
            [overtone.at-at :refer [every]]
            [org.zalando.stups.mint.worker.external.storage :as storage]
            [org.zalando.stups.mint.worker.external.services :as services]
            [org.zalando.stups.friboo.config :as config]
            [org.zalando.stups.mint.worker.external.apps :as apps]))

(def default-configuration
  {:jobs-cpu-count        1
   :jobs-every-ms         10000
   :jobs-initial-delay-ms 1000})

(defn create-username
  "Creates a username based on an application ID with optional prefix."
  [{:keys [prefix]} app-id]
  (if prefix
    (str prefix app-id)
    app-id))

(defn sync-app [configuration app-id]
  (let [username (create-username configuration app-id)
        storage-url (config/require-config configuration :storage-url)
        kio-url (config/require-config configuration :storage-url)
        service-user-url (config/require-config configuration :storage-url)
        kio-app (apps/get-app kio-url app-id)]
    ; TODO handle 404 from kio for app
    (if-not (:active kio-app)
      ; inactive app, check if deletion is required
      (let [users (services/list-users service-user-url)]
        (if (contains? users username)
          (do
            (log/info "App %s is inactive; deleting user %s..." app-id username)
            (services/delete-user service-user-url username))
          (log/debug "App %s is inactive and has no user." app-id)))
      ; active app, sync base data
      (let [app (storage/get-app storage-url app-id)]
        (log/info "Synchronizing app %s..." app)))))

(defn run-sync
  "Creates and deletes applications, rotates and distributes their credentials."
  [configuration]
  (let [storage-url (config/require-config configuration :storage-url)]
    (log/info "Starting new synchronisation run with %s..." configuration)
    (try
      (let [apps (storage/list-apps storage-url)]
        (doseq [app apps]
          (try
            (sync-app configuration (:id app))
            (catch Exception e
              (log/error e "Could not synchronize app %s because %s." app (str e)))))
        ; TODO delete users that are not managed apps anymore (remember optional prefix)
        )
      (catch Exception e
        (log/error e "Could not synchronize apps because %s." (str e))))))

(def-cron-component
  Jobs []

  (let [{:keys [every-ms initial-delay-ms]} configuration]

    (every every-ms (partial run-sync configuration) pool :initial-delay initial-delay-ms :desc "synchronisation")))
