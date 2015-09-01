(ns org.zalando.stups.mint.worker.external.storage
  (:require [org.zalando.stups.friboo.ring :refer [conpath]]
            [clj-http.client :as client]
            [org.zalando.stups.friboo.system.oauth2 :as oauth2]
            [clojure.string :as str]))

(defn list-apps
  "GET /apps

   [{'id': '123'}, {'id': '456'}]"
  [storage-url tokens]
  (:body (client/get (conpath storage-url "/apps")
                     {:oauth-token (oauth2/access-token :mint-storage-rw-api tokens)
                      :as          :json})))

(defn get-app
  "GET /apps/{applicationId}"
  [storage-url app-id tokens]
  {:pre [(not (str/blank? app-id))]}
  (:body (client/get (conpath storage-url "/apps/" app-id)
                     {:oauth-token (oauth2/access-token :mint-storage-rw-api tokens)
                      :as          :json})))

(defn update-status
  "PATCH /apps/{applicationId}"
  [storage-url app-id body tokens]
  {:pre [(not (str/blank? app-id))]}
  (client/patch (conpath storage-url "/apps/" app-id)
                {:oauth-token  (oauth2/access-token :mint-storage-rw-api tokens)
                 :content-type :json
                 :form-params  body
                 :as           :json})
  nil)
