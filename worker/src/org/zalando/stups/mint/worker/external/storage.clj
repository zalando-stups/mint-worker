(ns org.zalando.stups.mint.worker.external.storage
  (:require [org.zalando.stups.mint.worker.util :as util]))

(defn get-managed-apps [storage-url]
  (util/fetch storage-url "/apps"))

(defn get-app-details [storage-url app-id]
  (util/fetch storage-url "/apps/" app-id))
