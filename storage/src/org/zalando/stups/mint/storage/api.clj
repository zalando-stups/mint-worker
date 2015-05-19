; Copyright 2015 Zalando SE
;
; Licensed under the Apache License, Version 2.0 (the "License")
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
;
;     http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.

(ns org.zalando.stups.mint.storage.api
  (:require [org.zalando.stups.mint.storage.sql :as sql]
            [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.friboo.ring :refer :all]
            [org.zalando.stups.friboo.config :refer [require-config]]
            [org.zalando.stups.friboo.system.http :refer [def-http-component]]
            [org.zalando.stups.mint.storage.external.apps :refer [get-app]]
            [io.sarnowski.swagger1st.util.api :refer [throw-error]]
            [ring.util.response :refer :all]
            [clojure.data.json :refer [JSONWriter]]
            [clojure.java.jdbc :as jdbc]
            [clojure.set :as set]
            [clojure.string :as str]
            [clj-time.coerce :refer [to-sql-time from-sql-time]]))

; define the API component and its dependencies
(def-http-component API "api/mint-api.yaml" [db config])

(def default-http-configuration
  {:http-port 8080})

(defn- scopes-compared
  [scope1 scope2]
  (let [resource-type-id-compared (compare (:resource_type_id scope1) (:resource_type_id scope2))]
    (if (not= resource-type-id-compared 0)
      resource-type-id-compared
      (compare (:scope_id scope1) (:scope_id scope2)))))

(defn- strip-prefix
  "Removes the database field prefix."
  [m]
  (let [prefix-pattern #"[a-z]+_(.+)"
        remove-prefix (fn [k]
                        (->> k name (re-find prefix-pattern) second keyword))]
    (into {} (map
               (fn [[k v]] [(remove-prefix k) v])
               m))))

(defn- parse-s3-buckets
  "Splits a comma-separated string into a sorted set"
  [string]
  (if string
    (->> (str/split string #",")
         (remove str/blank?)
         (apply sorted-set))
    (sorted-set)))

(defn- load-scopes
  [application_id db]
  (->> (sql/read-scopes {:application_id application_id} {:connection db})
       (map strip-prefix)
       (apply sorted-set-by scopes-compared)))

(defn- load-application
  [application_id db]
  (when-first [row (sql/read-application {:application_id application_id} {:connection db})]
    (-> row
        strip-prefix
        (update-in [:s3_buckets] parse-s3-buckets)
        (update-in [:last_synced] from-sql-time)
        (update-in [:last_modified] from-sql-time)
        (update-in [:last_client_rotation] from-sql-time)
        (update-in [:last_password_rotation] from-sql-time)
        (assoc :scopes (load-scopes application_id db)))))

(defn read-applications
  "Returns all application configurations."
  [{:keys [resource_type_id scope_id]} _ db _]
  (let [db-result
        (if (or resource_type_id scope_id)
          (do (log/debug "Reading application configurations with resource_type '%s' and scope '%s'..."
                         resource_type_id scope_id)
              (sql/filter-applications {:resource_type_id resource_type_id :scope_id scope_id} {:connection db}))
          (do (log/debug "Reading application configurations with resource_type '%s' and scope '%s'..."
                         resource_type_id scope_id)
              (sql/read-applications {} {:connection db})))]
    (->> db-result
         (map strip-prefix)
         (map #(update-in % [:last_synced] from-sql-time))
         (map #(update-in % [:last_modified] from-sql-time))
         (map #(update-in % [:last_client_rotation] from-sql-time))
         (map #(update-in % [:last_password_rotation] from-sql-time))
         (response)
         (content-type-json))))

(defn read-application
  "Returns detailed information about one application configuration."
  [{:keys [application_id]} _ db _]
  (log/debug "Reading information about application %s..." application_id)
  (if-let [app (load-application application_id db)]
    (let [app (select-keys app [:id
                                :username
                                :client_id
                                :last_password_rotation
                                :last_client_rotation
                                :last_modified
                                :last_synced
                                :has_problems
                                :redirect_url
                                :is_client_confidential
                                :s3_buckets
                                :scopes])]
      (log/debug "Found application %s with %s." application_id app)
      (content-type-json (response app)))
    (not-found nil)))

(defn create-or-update-application
  "Creates or updates an appliction. If no s3 buckets are given, deletes the application."
  [{:keys [application_id application]} {:keys [configuration tokeninfo]} db mint-config]
  (log/debug "Creating or updating application %s with %s..." application_id application)
  (if (empty? (:s3_buckets application))
    (do
      (sql/delete-application! {:application_id application_id} {:connection db})
      (log/info "Deleted application %s because no s3 buckets were given." application_id))
    (do
      (if tokeninfo
        (when-not (get-app (require-config configuration :kio-url) application_id (get tokeninfo "access_token"))
          (throw-error
            400
            (str "Application '" application_id "' does not exist in Kio")
            {:invalid_application_id application_id}))
        (log/warn "Could not verify if app exists in Kio, because security was disabled (no HTTP_TOKENINFO_URL set)"))
      (jdbc/with-db-transaction
        [connection db]
        ; check app base information
        (let [db-app (load-application application_id db)
              new-s3-buckets (apply sorted-set (:s3_buckets application))
              new-scopes (apply sorted-set-by scopes-compared (:scopes application))
              prefix (:username-prefix mint-config)
              username (if prefix (str prefix application_id) application_id)]
          ; sync app
          (if db-app
            ; check for update (did anything change?)
            (when-not (and (= (:redirect_url db-app) (:redirect_url application))
                           (= (:is_client_confidential db-app) (:is_client_confidential application))
                           (= (:s3_buckets db-app) new-s3-buckets)
                           (= (:scopes db-app) new-scopes))
              (sql/update-application! {:application_id         application_id
                                        :redirect_url           (:redirect_url application)
                                        :is_client_confidential (:is_client_confidential application)
                                        :s3_buckets             (str/join "," new-s3-buckets)}
                                       {:connection connection}))
            ; create new app
            (sql/create-application! {:application_id         application_id
                                      :redirect_url           (:redirect_url application)
                                      :is_client_confidential (:is_client_confidential application)
                                      :s3_buckets             (str/join "," new-s3-buckets)
                                      :username               username}
                                     {:connection connection}))
          ; sync scopes
          (let [scopes-to-be-created (set/difference new-scopes (:scopes db-app))
                scopes-to-be-deleted (set/difference (:scopes db-app) new-scopes)]
            (doseq [scope scopes-to-be-created]
              (sql/create-scope! {:application_id   application_id
                                  :resource_type_id (:resource_type_id scope)
                                  :scope_id         (:scope_id scope)}
                                 {:connection connection}))
            (doseq [scope scopes-to-be-deleted]
              (sql/delete-scope! {:application_id   application_id
                                  :resource_type_id (:resource_type_id scope)
                                  :scope_id         (:scope_id scope)}
                                 {:connection connection})))))
      (log/info "Updated application %s with %s." application_id application)))
  (response nil))

(defn update-application-status
  "Updates an existing application."
  [{:keys [application_id status]} _ db _]
  (log/debug "Update application status %s ..." application_id)
  (if (pos? (sql/update-application-status! {:application_id         application_id
                                             :client_id              (:client_id status)
                                             :last_password_rotation (to-sql-time (:last_password_rotation status))
                                             :last_client_rotation   (to-sql-time (:last_client_rotation status))
                                             :last_synced            (to-sql-time (:last_synced status))
                                             :has_problems           (:has_problems status)}
                                            {:connection db}))
    (do (log/info "Updated application status %s with %s." application_id status)
        (response nil))
    (not-found nil)))

(defn delete-application
  "Deletes an application configuration."
  [{:keys [application_id]} _ db _]
  (log/debug "Delete application %s ..." application_id)
  (let [deleted (pos? (sql/delete-application! {:application_id application_id} {:connection db}))]
    (if deleted
      (do (log/info "Deleted application %s." application_id)
          (response nil))
      (not-found nil))))
