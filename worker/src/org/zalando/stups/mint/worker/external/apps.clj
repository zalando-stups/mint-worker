(ns org.zalando.stups.mint.worker.external.apps
  (:require [org.zalando.stups.mint.worker.util :as util]))

(defn get-app
  "GET /apps/{applicationId}"
  [kio-url app-id]
  (util/fetch kio-url "/apps/" app-id))
