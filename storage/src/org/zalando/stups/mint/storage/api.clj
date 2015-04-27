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
  (:require [org.zalando.stups.friboo.system.http :refer [def-http-component]]
            [org.zalando.stups.mint.storage.sql :as sql]
            [clojure.data.json :refer [JSONWriter]]
            [ring.util.response :refer :all]
            [org.zalando.stups.friboo.ring :refer :all]
            [org.zalando.stups.friboo.log :as log]
            [clojure.java.jdbc :as jdbc]))

; define the API component and its dependencies
(def-http-component API "api/mint-api.yaml" [db config])

(def default-http-configuration
  {:http-port 8080})

(defn- strip-prefix
  "Removes the database field prefix."
  [m]
  (let [prefix-pattern #"[a-z]+_(.+)"
        remove-prefix (fn [k]
                        (->> k name (re-find prefix-pattern) second keyword))]
    (into {} (map
               (fn [[k v]] [(remove-prefix k) v])
               m))))

(defn read-applications
  "Returns all application configurations."
  [_ _ db _]
  (log/debug "Listing all application configurations...")
  (->> (sql/read-applications {} {:connection db})
       (map #(strip-prefix %))
       (response)
       (content-type-json)))

(defn read-application
  "Returns detailed information about one application configuration."
  [{:keys [application_id]} _ db _]
  (log/debug "Reading information about application %s..." application_id)
  (if-let [app (first (sql/read-application {:application_id application_id} {:connection db}))]
    (let [app (-> app
                  (strip-prefix)
                  (select-keys [:id
                                :username
                                :last_password_rotation
                                :last_client_rotation
                                :last_modified
                                :last_synced
                                :has_problems
                                :redirect_url])
                  (assoc :accounts (->> (sql/read-accounts {:application_id application_id} {:connection db})
                                        (map #(strip-prefix %)))))]
      (log/debug "Found application %s with %s." application_id app)
      (-> (response app)
          (content-type-json)))
    (not-found nil)))

(defn create-or-update-application
  "Creates or updates an appliction. If no accounts are given, deletes the application."
  [{:keys [application_id application]} _ db config]
  (log/debug "Creating or updating application %s with %s..." application_id application)
  (if (empty? (:accounts application))
    (do
      (sql/delete-application! {:application_id application_id} {:connection db})
      (log/info "Deleted application %s because no accounts were given." application_id))
    (do
      (jdbc/with-db-transaction
        [connection db]
        ; sync accounts
        (let [accounts-now (sql/read-accounts {:application_id application_id} {:connection connection})
              accounts-future (:accounts application)
              account-matches? (fn [db-acc api-acc]
                                 (and (= (:ac_id db-acc) (:id api-acc))
                                      (= (:ac_type db-acc) (:type api-acc))))
              accounts-to-be-deleted (remove
                                       (fn [db-acc]
                                         (some #(account-matches? db-acc %) accounts-future))
                                       accounts-now)
              accounts-to-be-created (remove
                                       (fn [api-acc]
                                         (some #(account-matches? % api-acc) accounts-now))
                                       accounts-future)]
          ; check app base information
          (if-let [app (first (sql/read-application {:application_id application_id} {:connection connection}))]
            ; check for update (either redirect_url or accounts have changed)
            (if (or (not (= (:ap_redirect_url app) (:redirect_url application)))
                    (> (+ (count accounts-to-be-created) (count accounts-to-be-deleted)) 0))
              (sql/update-application! {:application_id application_id
                                        :redirect_url   (:redirect_url application)}
                                       {:connection connection}))
            ; create new app
            (let [prefix (:username-prefix config)
                  username (if prefix (str prefix application_id) application_id)]
              (sql/create-application! {:application_id application_id
                                        :redirect_url   (:redirect_url application)
                                        :username       username}
                                       {:connection connection})))
          (doseq [db-acc accounts-to-be-deleted]
            (sql/delete-account! {:application_id application_id
                                  :account_id     (:ac_id db-acc)
                                  :account_type   (:ac_type db-acc)}
                                 {:connection connection}))
          (doseq [api-acc accounts-to-be-created]
            (sql/create-account! {:application_id application_id
                                  :account_id     (:id api-acc)
                                  :account_type   (:type api-acc)}
                                 {:connection connection}))))
      (log/info "Updated application %s with %s." application_id application)))
  (response nil))

(defn update-application-status
  "Updates an existing application."
  [{:keys [application_id status]} _ db _]
  (log/debug "Update application status %s ..." application_id)
  (let [updated (> (sql/update-application-status! {:application_id         application_id
                                                    :client_id              (:client_id status)
                                                    :last_password_rotation (:last_password_rotation status)
                                                    :last_client_rotation   (:last_client_rotation status)
                                                    :last_synced            (:last_synced status)
                                                    :has_problems           (:has_problems status)}
                                                   {:connection db})
                   0)]
    (if updated
      (do (log/info "Updated application status %s with %s." application_id status)
          (response nil))
      (not-found nil))))

(defn delete-application
  "Deletes an application configuration."
  [{:keys [application_id]} _ db _]
  (log/debug "Delete application %s ..." application_id)
  (let [deleted (> (sql/delete-application! {:application_id application_id} {:connection db})
                   0)]
    (if deleted
      (do (log/info "Deleted application %s." application_id)
          (response nil))
      (not-found nil))))
