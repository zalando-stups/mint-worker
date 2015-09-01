(ns org.zalando.stups.mint.worker.external.apps
  (:require [org.zalando.stups.friboo.ring :refer [conpath]]
            [clj-http.client :as client]
            [org.zalando.stups.friboo.system.oauth2 :as oauth2]
            [clojure.string :as str]))

(defn get-app
  "GET /apps/{applicationId}"
  [kio-url app-id tokens]
  {:pre [(not (str/blank? kio-url))
         (not (str/blank? app-id))]}
  (:body (client/get (conpath kio-url "/apps/" app-id)
                     {:oauth-token (oauth2/access-token :kio-ro-api tokens)
                      :as          :json})))
