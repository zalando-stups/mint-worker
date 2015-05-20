(ns org.zalando.stups.mint.storage.external.apps
  (:require [clj-http.client :as client]
            [org.zalando.stups.friboo.ring :refer [conpath]]
            [slingshot.slingshot :refer [try+]]
            [com.netflix.hystrix.core :as hystrix]))

(hystrix/defcommand
  get-app
  "GET /apps/{applicationId}"
  [kio-url access-token app-id]
  (try+
    (:body (client/get (conpath kio-url "/apps/" app-id)
                       {:oauth-token access-token
                        :as          :json}))
    (catch [:status 404] _
      nil)))
