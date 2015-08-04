(ns org.zalando.stups.mint.worker.job
  (:require [org.zalando.stups.friboo.system.cron :refer [def-cron-component job]]
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
                (if (= :owner-scope (:scope-type scope))
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
  (let [storage-url (config/require-config configuration :mint-storage-url)
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
          (log/debug "App %s is inactive and has no user." app-id))
        app)

      ; active app, check for last sync
      (if (or (nil? (:last_synced app))
              (time/after? (parse-date-time (:last_modified app)) (parse-date-time (:last_synced app))))
        (do
          (log/info "Synchronizing app %s..." app)
          (let [scopes (map-scopes (:scopes app) configuration tokens)
                scopes (update-in scopes [:owner-scope] conj
                                  {:realm  "services"       ; TODO hardcoded assumption of services realm and uid and no other services-owned scopes!! fix asap
                                   :scopes (conj (:application-scope scopes) "uid")})
                scopes (update-in scopes [:owner-scope] conj
                                  {:realm  "employees"       ; TODO hardcoded assumption that we currently have no employee scopes!! fix asap
                                   :scopes ["uid"]})
                response (services/create-or-update-user service-user-url
                                                         username
                                                         {:id            username
                                                          :name          (:name kio-app)
                                                          :owner         team-id
                                                          :client_config {:redirect_urls (if (empty? (:redirect_url app))
                                                                                           []
                                                                                           [(:redirect_url app)])
                                                                          :scopes        (:owner-scope scopes)
                                                                          :confidential  (:is_client_confidential app)}
                                                          :user_config   {:scopes (:application-scope scopes)}}
                                                         tokens)
                new-client-id (:client_id response)]

            (when (and (not (:is_client_confidential app)) (nil? (:client_id app)))
              (log/info "Saving non-confidential client ID %s for app %s..." new-client-id app-id)
              (doseq [bucket-name bucket-names]
                (s3/save-client bucket-name app-id new-client-id nil)))

            (log/debug "Updating last synced time and client_id for app %s..." app-id)
            (storage/update-status storage-url app-id {:last_synced (format-date-time (time/now)) :client_id new-client-id} tokens)

            (log/info "Successfully synced app %s" app-id)
            (assoc app :client_id new-client-id)))

        ; else
        (do
          (log/debug "App %s has not been modified since last sync. Skip sync." app-id)
          app)))))

(defn sync-password
  "If neccessary, creates and syncs a new password for the given app."
  [app configuration tokens]
  (let [storage-url (config/require-config configuration :mint-storage-url)
        service-user-url (config/require-config configuration :service-user-url)
        username (:username app)
        app-id (:id app)]
    (if (or (nil? (:last_password_rotation app))
            (time/after? (time/now)
                         (-> (parse-date-time (:last_password_rotation app)) (time/plus (time/hours 2)))))
      (do
        ; Step 1: generate password
        (log/info "Acquiring new password for %s..." username)
        (let [{:keys [password txid]} (services/generate-new-password service-user-url username tokens)
              bucket-names (:s3_buckets app)]
          ; Step 2: distribute it
          (log/info "Saving the new password for %s to S3 buckets: %s..." app-id bucket-names)
          (doseq [bucket-name bucket-names]
            (s3/save-user bucket-name app-id username password))

          ; Step 3: if successful distributed, commit it
          (log/debug "Committing new password for app %s with transaction %s..." app-id txid)
          (services/commit-password service-user-url username txid tokens)

          ; Step 4: store last rotation time (now)
          (log/debug "Saving last password rotation status for app %s..." app-id)
          (storage/update-status storage-url app-id {:last_password_rotation (format-date-time (time/now))} tokens)

          (log/info "Successfully rotated password for app %s" app-id)))

      ; else
      (log/debug "Password for app %s is still valid. Skip password rotation." app-id))))

(defn sync-client
  "If neccessary, creates and syncs new client credentials for the given app"
  [app configuration tokens]
  (let [service-user-url (config/require-config configuration :service-user-url)
        storage-url (config/require-config configuration :mint-storage-url)
        app-id (:id app)
        client-id (:client_id app)
        username (:username app)
        bucket-names (:s3_buckets app)]
    (if (or (nil? (:last_client_rotation app))
            (time/after? (time/now) (time/plus (parse-date-time (:last_client_rotation app)) (time/months 1))))
      (do
        ; Step 1: generate password
        (log/info "Acquiring a new client for app %s..." app-id)
        (let [generate-client-response (services/generate-new-client service-user-url username client-id tokens)
              client-id (:client_id generate-client-response)
              transaction-id (:txid generate-client-response)
              client-secret (:client_secret generate-client-response)]
          ; Step 2: distribute it
          (log/info "Saving the new client for %s to S3 buckets: %s..." app-id bucket-names)
          (doseq [bucket-name bucket-names]
            (s3/save-client bucket-name app-id client-id client-secret))

          ; Step 3: if successful distributed, commit it
          (log/debug "Committing new secret for app %s with transaction %s..." app-id transaction-id)
          (services/commit-client service-user-url username transaction-id tokens)

          ; Step 4: store last rotation time (now)
          (log/debug "Saving last client rotation status for app %s..." app-id)
          (storage/update-status storage-url app-id {:last_client_rotation (format-date-time (time/now))
                                                     :client_id            client-id} tokens)

          (log/info "Successfully rotated client for app %s" app-id)))

      ; else
      (log/debug "Client for app %s is still valid. Skip client rotation." app-id))))

(defn sync-app
  "Syncs the application with the given app-id."
  [configuration app-id tokens]
  (log/debug "============================== %s ==============================" app-id)
  (log/debug "Start syncing app %s..." app-id)
  (let [storage-url (config/require-config configuration :mint-storage-url)
        kio-url (config/require-config configuration :kio-url)
        app (storage/get-app storage-url app-id tokens)
        kio-app (apps/get-app kio-url app-id tokens)]
    ; TODO handle 404 from kio for app
    (log/debug "App has mint configuration %s" app)
    (log/debug "App has kio configuration %s" kio-app)
    (let [app (sync-user app kio-app configuration tokens)]
      (if (:active kio-app)
        (do
          (sync-password app configuration tokens)
          (if (:is_client_confidential app)
            (sync-client app configuration tokens)
            (log/debug "Skipping client rotation for non-confidential client %s" app-id)))
        (log/debug "Skipping password and client rotation for inactive app %s" app-id))
      (log/debug "Synced app %s." app-id))))

; used to track rate limiting of service user api
(def rate-limited-until (atom (time/epoch)))

(defn run-sync
  "Creates and deletes applications, rotates and distributes their credentials."
  [configuration tokens]
  (try
    (when (time/after? (time/now) @rate-limited-until)
      (let [storage-url (config/require-config configuration :mint-storage-url)]
        (log/debug "Starting new synchronisation run with %s..." configuration)

        (let [apps (storage/list-apps storage-url tokens)]
          (log/debug "Found apps: %s ..." apps)
          (doseq [app apps]
            (try
              (sync-app configuration (:id app) tokens)
              (storage/update-status storage-url (:id app) {:has_problems false} tokens)
              (catch Exception e
                (when (= 429 (:status  (ex-data e)))
                                        ; bubble up if we are rate limited
                  (throw e))
                (storage/update-status storage-url (:id app)
                                                   {:has_problems true
                                                    :message (str e)}
                                                   tokens)
                (log/warn "Could not synchronize app %s because %s." (:id app) (str e))))))))
    (catch Throwable e
      (if (= 429 (:status  (ex-data e)))
        (do
          (log/warn "We got rate limited; pausing activities for the next 90 seconds.")
          (reset! rate-limited-until (time/plus (time/now) (time/seconds 90))))
        (log/error e "Could not synchronize apps because %s." (str e))))))

(def-cron-component
  Jobs [tokens]

  (let [{:keys [every-ms initial-delay-ms]} configuration]

    (every every-ms (job run-sync configuration tokens) pool :initial-delay initial-delay-ms :desc "synchronisation")))


