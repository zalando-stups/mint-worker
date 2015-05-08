(ns org.zalando.stups.mint.worker.job
  (:require [org.zalando.stups.friboo.system.cron :refer [def-cron-component]]
            [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.friboo.config :as config]
            [overtone.at-at :refer [every]]
            [org.zalando.stups.mint.worker.external.storage :as storage]
            [org.zalando.stups.mint.worker.external.services :as services]
            [org.zalando.stups.mint.worker.external.apps :as apps]
            [org.zalando.stups.mint.worker.external.s3 :as s3]
            [org.zalando.stups.mint.worker.external.scopes :as scopes]
            [clj-time.core :as time]
            [clj-time.format :as f]))

(def default-configuration
  {:jobs-cpu-count        1
   :jobs-every-ms         10000
   :jobs-initial-delay-ms 1000})

(defn- parse-date-time
  [string]
  (when string (f/parse (f/formatters :date-time) string)))

(defn- format-date-time
  [date-time]
  (f/unparse (f/formatters :date-time) date-time))

(defn owners [{:keys [resource_type_id]} essentials-url tokens]
  (:resource_owners (scopes/get-resource-type essentials-url resource_type_id tokens)))

(defn owner-scope? [{:keys [resource_type_id scope_id]} essentials-url tokens]
  (:is_resource_owner_scope (scopes/get-scope essentials-url resource_type_id scope_id tokens)))

(defn group-types [scopes configuration tokens]
  (let [essentials-url (config/require-config configuration :essentials-url)]
    (->> scopes
         (map #(assoc % :id (str (:resource_type_id %) "." (:scope_id %))))
         (map #(assoc % :scope-type (if (owner-scope? % essentials-url tokens) :owner-scope :application-scope)))
         (map (fn [scope]
                #(if (= :owner-scope (:scope-type scope))
                  (assoc scope :owners (owners scope essentials-url tokens))
                  scope)))
         (group-by :scope-type))))

(defn add-scope [result scope-id [owner & owners]]
  (if owner
    (let [result (update-in result [owner] conj scope-id)]
      (recur result scope-id owners))
    result))

(defn map-owner-scopes [scopes]
  (->> scopes
       (reduce
         (fn [result scope]
           (add-scope result (:id scope) (:owners scope)))
         {})
       (map (fn [[owner scopes]]
              {:realm owner
               :sopes scopes}))))

(defn map-application-scopes [scopes]
  (map :id scopes))

(defn map-scopes [scopes configuration tokens]
  (-> scopes
      (group-types configuration tokens)
      (update-in [:owner-scope] map-owner-scopes)
      (update-in [:application-scope] map-application-scopes)))

(defn sync-user
  "If neccessary, creates or deletes service users."
  [app kio-app configuration tokens]
  (let [storage-url (config/require-config configuration :storage-url)
        service-user-url (config/require-config configuration :service-user-url)
        app-id (:id app)
        team-id (:team_id kio-app)
        username (:username app)
        bucket-names (:s3_buckets app)]
    (if-not (:active kio-app)
      ; inactive app, check if deletion is required
      (let [users (services/list-users service-user-url tokens)]
        (if (contains? users username)
          (do
            (log/info "App %s is inactive; deleting user %s..." app-id username)
            (services/delete-user service-user-url username tokens))
          (log/debug "App %s is inactive and has no user." app-id)))

      ; active app, check for last sync
      (if (or (nil? (:last_synced app))
              (time/after? (parse-date-time (:last_modified app)) (parse-date-time (:last_synced app))))
        (do
          (log/info "Synchronizing app %s..." app)
          (let [scopes (map-scopes (:scopes app) configuration tokens)
                response (services/create-or-update-user service-user-url
                                                         username
                                                         {:id            username
                                                          :name          (:name kio-app)
                                                          :owner         team-id
                                                          :client_config {:redirect_urls [(:redirect_url app)]
                                                                          :scopes        (:owner-scope scopes)
                                                                          :confidential  (:is_client_confidential app)} ; TODO is this key correct?
                                                          :user_config   {:scopes (:application-scope scopes)}}
                                                         tokens)
                new-client-id (:client_id response)]        ; TODO is this correct?
            (if (:client_id app)
              (do
                (log/info "Updating last_synced time of application %s..." app-id)
                (storage/update-status storage-url app-id {:last_synced (format-date-time (time/now))} tokens))
              (do
                (log/info "Saving new client for app %s..." app-id)
                (doseq [bucket-name bucket-names]
                  (s3/save-client bucket-name app-id new-client-id nil))
                (log/info "Updating last synced time and client_id for app %s" app-id)
                (storage/update-status storage-url app-id {:last_synced (format-date-time (time/now)) :client_id new-client-id} tokens))))
          (log/info "Successfully synced application %s" app-id))

        ; else
        (log/debug "App %s has not been modified since last sync. Skip sync." app-id)))))

(defn sync-password
  "If neccessary, creates and syncs a new password for the given app."
  [app configuration tokens]
  (let [storage-url (config/require-config configuration :storage-url)
        service-user-url (config/require-config configuration :service-user-url)
        username (:username app)
        app-id (:id app)]
    (if (or (nil? (:last_password_rotation app))
            (time/after? (time/now)
                         (-> (parse-date-time (:last_password_rotation app)) (time/plus (time/hours 2)))))
      (do
        (log/info "Acquiring new password for %s..." username)
        (let [generate-pw-response (services/generate-new-password service-user-url username tokens)
              password (:password generate-pw-response)
              txid (:txid generate-pw-response)
              bucket-names (:s3_buckets app)]
          (try
            (do
              (log/info "Saving the new password for %s to S3 buckets: %s..." app-id bucket-names)
              (doseq [bucket-name bucket-names]
                (s3/save-user bucket-name app-id username password))
              (services/commit-password service-user-url username txid tokens)
              (log/info "Successfully rotated password for app %s" app-id))
            (catch Exception e
              (log/error e "Could not rotate password for app %s" app-id)
              (storage/update-status storage-url app-id {:has_problems true} tokens))))) ; todo when to recover has_problems?

      ; else
      (log/debug "Password for app %s is still valid. Skip password rotation." app-id))))

(defn sync-client
  "If neccessary, creates and syncs new client credentials for the given app"
  [app configuration tokens]
  (let [service-user-url (config/require-config configuration :service-user-url)
        storage-url (config/require-config configuration :storage-url)
        app-id (:id app)
        username (:username app)
        bucket-names (:s3_buckets app)]
    (if (or (nil? (:last_client_rotation app))
            (time/after? (time/now) (time/plus (parse-date-time (:last_client_rotation app)) (time/months 1))))
      (do
        (log/debug "Acquiring a new client for app %s..." app-id)
        (let [generate-client-response (services/generate-new-client service-user-url username tokens)
              client-id (:client_id generate-client-response)
              client-secret (:client_secret generate-client-response)]
          (try
            (do
              (log/info "Saving the new client for %s to S3 buckets: %s..." app-id bucket-names)
              (doseq [bucket-name bucket-names]
                (s3/save-client bucket-name app-id client-id client-secret))
              (services/commit-client service-user-url username client-id tokens)
              (log/info "Successfully rotated client for app %s" app-id))
            (catch Exception e
              (log/error e "Could not rotate client for app %s" app-id)
              (storage/update-status storage-url app-id {:has_problems true} tokens)))))

      ; else
      (log/info "Client for app %s is still valid. Skip client rotation." app-id))))

(defn sync-app
  "Syncs the application with the given app-id."
  [configuration app-id tokens]
  (log/debug "Start syncing app %s ..." app-id)
  (let [storage-url (config/require-config configuration :storage-url)
        kio-url (config/require-config configuration :kio-url)
        app (storage/get-app storage-url app-id tokens)
        kio-app (apps/get-app kio-url app-id tokens)]
    ; TODO handle 404 from kio for app
    (log/debug "App has mint configuration %s" app)
    (log/debug "App has kio configuration %s" kio-app)
    (sync-user app kio-app configuration tokens)
    (if (:active kio-app)
      (if (:is_client_confidential app)
        (do
          (sync-password app configuration tokens)
          (sync-client app configuration tokens))
        (log/debug "Skip password and client rotation for public client %s" app-id))
      (log/debug "Skip password and client rotation for inactive app %s" app-id))))

(defn run-sync
  "Creates and deletes applications, rotates and distributes their credentials."
  [configuration tokens]
  (let [storage-url (config/require-config configuration :storage-url)]
    (log/info "Starting new synchronisation run with %s..." configuration)
    (try
      (let [apps (storage/list-apps storage-url tokens)]
        (log/debug "Found apps: %s ..." apps)
        (doseq [app apps]
          (try
            (sync-app configuration (:id app) tokens)
            (catch Exception e
              (log/error e "Could not synchronize app %s because %s." app (str e))))))
      (catch Exception e
        (log/error e "Could not synchronize apps because %s." (str e))))))

(def-cron-component
  Jobs [tokens]

  (let [{:keys [every-ms initial-delay-ms]} configuration]

    (every every-ms #(run-sync configuration tokens) pool :initial-delay initial-delay-ms :desc "synchronisation")))


