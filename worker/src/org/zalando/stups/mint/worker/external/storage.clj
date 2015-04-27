(ns org.zalando.stups.mint.worker.external.storage
  (:require [org.zalando.stups.mint.worker.util :as util]))

(defn list-apps
  "GET /apps

   [{'id': '123'}, {'id': '456'}]"
  [storage-url]
  (util/fetch storage-url "/apps"))

(defn get-app
  "GET /apps/{applicationId}"
  [storage-url app-id]
  (util/fetch storage-url "/apps/" app-id))
