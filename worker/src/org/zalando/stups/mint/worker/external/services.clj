(ns org.zalando.stups.mint.worker.external.services
  (:require [org.zalando.stups.mint.worker.util :as util]))

(defn list-users
  "GET /services

   #{'user1', 'user2'}"
  [service-user-url]
  (into #{} (util/fetch service-user-url "/services")))

(defn delete-user
  [service-user-url username]
  (util/fetch-with nil :delete service-user-url "/services/" username))

(defn create-or-update-user
  [service-user-url username body]
  (util/fetch-with body :put service-user-url "/services/" username))

(defn generate-new-password
  [service-user-url username]
  (util/fetch-with nil :post service-user-url "/services/" username "/password"))

(defn commit-password
  [service-user-url username transaction-id]
  (util/fetch-with {:txid transaction-id} :put service-user-url "/services/" username "/password"))

(defn generate-new-client
  [service-user-url username]
  (util/fetch-with nil :post service-user-url "/services/" username "/client"))

(defn commit-client
  [service-user-url username client-id]
  (util/fetch-with {:id username :client:id client-id} :put service-user-url "/services/" username "/client"))
