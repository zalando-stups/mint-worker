(ns org.zalando.stups.mint.worker.job.sync-app
  (:require [org.zalando.stups.mint.worker.external.apps :as apps]
            [org.zalando.stups.mint.worker.external.storage :as storage]
            [org.zalando.stups.mint.worker.external.s3 :as s3]
            [org.zalando.stups.mint.worker.job.sync-app :refer [sync-app]]
            [org.zalando.stups.mint.worker.job.sync-client :refer [sync-client]]
            [org.zalando.stups.mint.worker.job.sync-user :refer [sync-user]]
            [org.zalando.stups.mint.worker.job.sync-password :refer [sync-password]]))

; it should not even try to test writability of buckets when max_errors is reached

; it should increase s3_errors when no bucket is writable

; it should do stuff when every bucket is writable

; errors are handled by calling function