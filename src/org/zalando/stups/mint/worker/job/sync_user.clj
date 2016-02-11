(ns org.zalando.stups.mint.worker.job.sync-user
  (:require [clj-time.core :as time]
            [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.friboo.config :as config]
            [org.zalando.stups.mint.worker.job.common :as c]
            [org.zalando.stups.mint.worker.external.services :as services]
            [org.zalando.stups.mint.worker.external.storage :as storage]
            [org.zalando.stups.mint.worker.external.s3 :as s3]
            [clojure.string :as str]))

(defn sync-user
  "If neccessary, creates or deletes service users."
  [{:keys [id username s3_buckets last_synced last_modified scopes redirect_url is_client_confidential client_id] :as app}
   {:keys [team_id active name subtitle]} ; kio app
   configuration
   tokens]
  {:pre [(not (str/blank? id))
         (seq s3_buckets)]}
  (let [storage-url (config/require-config configuration :mint-storage-url)
        shadow-user-url (config/require-config configuration :shadow-service-user-url)
        service-user-url (config/require-config configuration :service-user-url)]

    (if-not active
      ; inactive app, check if deletion is required
      (let [user-exists? (->> (services/list-users service-user-url tokens)
                              (map :id)
                              (set))
            shadow-user-exists? (->> (services/list-users shadow-user-url tokens)
                                     (map :id)
                                     (set))]
        ; delete from primary
        (if (user-exists? username)
          (do
            (services/delete-user service-user-url username tokens)
            (log/info "App %s is inactive; deleted user %s from primary." id username))
          (log/debug "App %s is inactive and has no user in primary." id))
        ; delete from shadow
        (if (shadow-user-exists? username)
          (do
            (services/delete-user shadow-user-url username tokens)
            (log/info "App %s is inactive; deleted user %s from shadow." id username))
          (log/debug "App %s is inactive and has no user in shadow." id))
        app)

      ; active app, check for last sync
      (if (or (nil? last_synced)
              (time/after? (c/parse-date-time last_modified)
                           (c/parse-date-time last_synced)))
        (do
          (log/debug "Synchronizing app %s..." app)
          (let [scopes (c/map-scopes scopes configuration tokens)
                scopes (update-in scopes [:owner-scope] conj
                                  {:realm  "services"       ; TODO hardcoded assumption of services realm and uid and no other services-owned scopes!! fix asap
                                   :scopes (conj (:application-scope scopes) "uid")})
                scopes (update-in scopes [:owner-scope] conj
                                  {:realm  "employees"       ; TODO hardcoded assumption that we currently have no employee scopes!! fix asap
                                   :scopes ["uid"]})
                body   {:id            username
                        :name          name
                        :owner         team_id
                        :subtitle      subtitle
                        :client_config {:redirect_urls (if (str/blank? redirect_url)
                                                         []
                                                         [redirect_url])
                                        :scopes        (:owner-scope scopes)
                                        :confidential  is_client_confidential}
                        :user_config   {:scopes (:application-scope scopes)}}
                ; create in primary
                primary-response (services/create-or-update-user service-user-url
                                                                 username
                                                                 body
                                                                 tokens)
                new-client-id (:client_id primary-response)]
            ; create in shadow
            (log/debug "Creating/updating app %s in shadow..." username)
            (services/create-or-update-user shadow-user-url
                                            username
                                            (assoc body :client_id new-client-id)
                                            tokens)
            (when (and (not is_client_confidential)
                       (nil? client_id))
              (log/debug "Saving non-confidential client ID %s for app %s..." new-client-id id)
              (when-let [error (c/has-error (c/busy-map #(s3/save-client % id new-client-id nil)
                                                        s3_buckets))]
                (log/debug "Could not save client ID: %s" (str error))
                ; throw to update s3_errors once outside
                (throw error)))

            (log/debug "Updating last synced time and client_id for app %s..." id)
            (storage/update-status storage-url
                                   id
                                   {:last_synced (c/format-date-time (time/now)) :client_id new-client-id}
                                   tokens)

            (log/info "Successfully synced user for app %s" id)
            (assoc app :client_id new-client-id)))

        ; else
        (do
          (log/debug "App %s has not been modified since last sync. Skip sync." id)
          app)))))