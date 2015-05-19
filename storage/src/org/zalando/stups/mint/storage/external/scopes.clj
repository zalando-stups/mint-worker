(ns org.zalando.stups.mint.storage.external.scopes
  (:require [clj-http.client :as client]
            [org.zalando.stups.friboo.ring :refer [conpath]]
            [slingshot.slingshot :refer [try+]]))

(defn get-scope
  "GET /resource-types/{resource_type_id}/scopes/{scope_id}"
  [resource-type-id scope-id essentials-url access-token]
  (try+
    (:body (client/get (conpath essentials-url "/resource-types/" resource-type-id "/scopes/" scope-id)
                       {:oauth-token access-token
                        :as          :json}))
    (catch [:status 404] _
      nil)))
