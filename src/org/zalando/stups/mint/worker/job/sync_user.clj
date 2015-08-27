(ns org.zalando.stups.mint.worker.job.sync-user
  (:require [clj-time.core :as time]
            [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.friboo.config :as config]
            [org.zalando.stups.mint.worker.job.common :as c]
            [org.zalando.stups.mint.worker.external.services :as services]
            [org.zalando.stups.mint.worker.external.storage :as storage]
            [org.zalando.stups.mint.worker.external.s3 :as s3]))

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
              (time/after? (c/parse-date-time (:last_modified app))
                           (c/parse-date-time (:last_synced app))))
        (do
          (log/info "Synchronizing app %s..." app)
          (let [scopes (c/map-scopes (:scopes app) configuration tokens)
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
              (if-let [error (c/has-error (c/busy-map #(s3/save-client % app-id new-client-id nil)
                                                      bucket-names))]
                (do
                  (log/debug "Could not save client ID: %s" (str error))
                  ; throw to update s3_errors once outside
                  (throw error))
                (do
                  (log/debug "Updating last synced time and client_id for app %s..." app-id)
                  (storage/update-status storage-url app-id
                                                     {:last_synced (c/format-date-time (time/now))
                                                      :client_id new-client-id}
                                                     tokens)

                  (log/info "Successfully synced app %s" app-id)
                  (assoc app :client_id new-client-id))))))

        ; else
        (do
          (log/debug "App %s has not been modified since last sync. Skip sync." app-id)
          app)))))