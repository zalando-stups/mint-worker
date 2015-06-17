(ns org.zalando.stups.mint.worker.external.services
  (:require [org.zalando.stups.mint.worker.util :refer [conpath]]
            [clj-http.client :as client]
            [org.zalando.stups.friboo.system.oauth2 :as oauth2]))

(defn list-users
  "GET /services

   #{'user1', 'user2'}"
  [service-user-url tokens]
  (into #{} (:body (client/get (conpath service-user-url "/services")
                               {:oauth-token (oauth2/access-token :service-user-rw-api tokens)
                                :as          :json}))))

(defn delete-user
  [service-user-url username tokens]
  (client/delete (conpath service-user-url "/services/" username)
                 {:oauth-token (oauth2/access-token :service-user-rw-api tokens)})
  nil)

(defn create-or-update-user
  [service-user-url username body tokens]
  (:body (client/put (conpath service-user-url "/services/" username)
                     {:oauth-token  (oauth2/access-token :service-user-rw-api tokens)
                      :content-type :json
                      :form-params  body
                      :as           :json})))

(defn generate-new-password
  [service-user-url username tokens]
  (:body (client/post (conpath service-user-url "/services/" username "/password")
                      {:oauth-token  (oauth2/access-token :service-user-rw-api tokens)
                       :content-type :json
                       :form-params  {}
                       :as           :json})))

(defn commit-password
  [service-user-url username transaction-id tokens]
  (:body (client/put (conpath service-user-url "/services/" username "/password")
                     {:oauth-token  (oauth2/access-token :service-user-rw-api tokens)
                      :content-type :json
                      :form-params  {:txid transaction-id}
                      :as           :json})))

(defn generate-new-client
  [service-user-url username client-id tokens]
  (:body (client/post (conpath service-user-url "/services/" username "/client")
                      {:oauth-token  (oauth2/access-token :service-user-rw-api tokens)
                       :content-type :json
                       :form-params  {:id username
                                      :client_id client-id}
                       :as           :json})))

(defn commit-client
  [service-user-url username transaction-id tokens]
  (:body (client/put (conpath service-user-url "/services/" username "/client")
                     {:oauth-token  (oauth2/access-token :service-user-rw-api tokens)
                      :content-type :json
                      :form-params  {:txid transaction-id}
                      :as           :json})))
