(ns org.zalando.stups.mint.worker.external.apps
  (:require [org.zalando.stups.friboo.ring :refer [conpath]]
            [org.zalando.stups.mint.worker.external.http :as client]
            [org.zalando.stups.friboo.system.oauth2 :as oauth2]
            [clojure.string :as str]))

(defn list-apps
  "GET /apps"
  [kio-url tokens]
  {:pre [(not (str/blank? kio-url))]}
  (-> kio-url
      (conpath "/apps")
      (client/get {:oauth-token (oauth2/access-token :kio-ro-api tokens)
                   :as :json})
      :body))
