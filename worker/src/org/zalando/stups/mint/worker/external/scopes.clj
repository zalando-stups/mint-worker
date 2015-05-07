(ns org.zalando.stups.mint.worker.external.scopes
  (:require [org.zalando.stups.mint.worker.util :as util]))

(defn get-scope
  "GET /resource-types/{resource_type_id}/scopes/{scope_id}"
  [essentials-url resource-type-id scope-id]
  (util/fetch essentials-url "/resource-types/" resource-type-id "/scopes/" scope-id))

(defn get-resource-type
  "GET /resource-types/{resource_type_id}"
  [essentials-url resource-type-id]
  (util/fetch essentials-url "/resource-types/" resource-type-id))
