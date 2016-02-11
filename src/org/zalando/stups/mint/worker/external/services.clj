(ns org.zalando.stups.mint.worker.external.services
  (:require [org.zalando.stups.friboo.ring :refer [conpath]]
            [clj-http.client :as client]
            [org.zalando.stups.friboo.system.oauth2 :as oauth2]
            [clojure.string :as str]))

; TODO we should cache the result, because it is used quite often and won't change a lot
(defn list-users
  "GET /services

   #{'user1', 'user2'}"
  [service-user-url tokens]
  (set (:body (client/get (conpath service-user-url "/services")
                          {:oauth-token (oauth2/access-token :service-user-rw-api tokens)
                           :as          :json}))))

(defn delete-user
  [service-user-url username tokens]
  {:pre [(not (str/blank? username))]}
  (client/delete (conpath service-user-url "/services/" username)
                 {:oauth-token (oauth2/access-token :service-user-rw-api tokens)})
  nil)

(defn create-or-update-user
  [service-user-url username body tokens]
  {:pre [(not (str/blank? username))]}
  (:body (client/put (conpath service-user-url "/services/" username)
                     {:oauth-token  (oauth2/access-token :service-user-rw-api tokens)
                      :content-type :json
                      :form-params  body
                      :as           :json})))

(defn generate-new-password
  [service-user-url username body tokens]
  {:pre [(not (str/blank? username))]}
  (:body (client/post (conpath service-user-url "/services/" username "/password")
                      {:oauth-token  (oauth2/access-token :service-user-rw-api tokens)
                       :content-type :json
                       :form-params  (or body {})
                       :as           :json})))

(defn commit-password
  [service-user-url username transaction-id tokens]
  {:pre [(not (str/blank? username))
         (not (str/blank? transaction-id))]}
  (:body (client/put (conpath service-user-url "/services/" username "/password")
                     {:oauth-token  (oauth2/access-token :service-user-rw-api tokens)
                      :content-type :json
                      :form-params  {:txid transaction-id}
                      :as           :json})))

(defn generate-new-client
  [service-user-url username {:keys [client_id client_secret txid]} tokens]
  {:pre [(not (str/blank? username))
         (not (str/blank? client_id))]}
  (:body (client/post (conpath service-user-url "/services/" username "/client")
                      {:oauth-token  (oauth2/access-token :service-user-rw-api tokens)
                       :content-type :json
                       :form-params  {:id username
                                      :txid txid
                                      :client_secret client_secret
                                      :client_id client_id}
                       :as           :json})))

(defn commit-client
  [service-user-url username transaction-id tokens]
  {:pre [(not (str/blank? username))
         (not (str/blank? transaction-id))]}
  (:body (client/put (conpath service-user-url "/services/" username "/client")
                     {:oauth-token  (oauth2/access-token :service-user-rw-api tokens)
                      :content-type :json
                      :form-params  {:txid transaction-id}
                      :as           :json})))
