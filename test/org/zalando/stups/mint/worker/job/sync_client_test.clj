(ns org.zalando.stups.mint.worker.job.sync-client-test
  (:require [clojure.test :refer :all]
            [clj-time.core :as time]
            [org.zalando.stups.mint.worker.job.common :as c]
            [org.zalando.stups.mint.worker.test-helpers :refer [test-tokens
                                                                test-config
                                                                sequentially
                                                                throwing
                                                                third
                                                                track]]
            [org.zalando.stups.mint.worker.job.sync-client :refer [sync-client]]
            [org.zalando.stups.mint.worker.external.services :as services]
            [org.zalando.stups.mint.worker.external.storage :as storage]
            [org.zalando.stups.mint.worker.external.bucket_storage :refer [save-client
                                                                           StorageException]]
            [org.zalando.stups.mint.worker.external.s3 :as s3])
  (:import (com.amazonaws.services.s3.model PutObjectResult)))

(def test-app
  {:id "kio"
   :client_id "kio-client"
   :username "stups_kio"
   :s3_buckets ["bucket-one" "bucket-two"]})

(def test-response
  {:client_id "kio-client2"
   :txid "123"
   :client_secret "lolz"})

; it should skip when last rotation was <= 1 month ago
(deftest should-skip-when-rotation-was-recently
  (let [recently (c/format-date-time (time/minus (time/now)
                                                 (time/weeks 3)))
        test-app (assoc test-app :last_client_rotation recently)
        calls (atom {})]
    (with-redefs [services/generate-new-client (track calls :gen)]
      (sync-client test-app
                   test-config
                   test-tokens)
      (is (= 0 (count (:gen @calls)))))))

; it should not skip when last rotation was > 1 month ago
(deftest should-not-skip-when-rotation-was-not-recently
  (let [past (c/format-date-time (time/minus (time/now)
                                             (time/weeks 5)))
        test-app (assoc test-app :last_client_rotation past)
        calls (atom {})]
    (with-redefs [services/generate-new-client (constantly test-response)
                  save-client (constantly (PutObjectResult.))
                  services/commit-client (track calls :commit)
                  storage/update-status (track calls :update)]
      (sync-client test-app
                   test-config
                   test-tokens)
      (is (= 1 (count (:commit @calls))))
      (is (= 1 (count (:update @calls))))
      (let [args (first (:commit @calls))]
        ; signature: storage-url username transaction-id
        (is (= (second args)
               (:username test-app)))
        (is (= (third args)
               (:txid test-response)))))))

; it should not skip when last rotation was never
(deftest should-not-skip-when-never-rotated
  (let [calls (atom {})]
    (with-redefs [services/generate-new-client (constantly test-response)
                  save-client (constantly (PutObjectResult.))
                  services/commit-client (track calls :commit)
                  storage/update-status (track calls :update)]
      (sync-client test-app
                   test-config
                   test-tokens)
      (is (= 1 (count (:commit @calls))))
      (is (= 1 (count (:update @calls)))))))

; it should commit password only after successful write to all buckets
(deftest should-not-commit-if-s3-write-failed
  (let [calls (atom {})]
    (with-redefs [services/generate-new-client (constantly test-response)
                  save-client (sequentially (PutObjectResult.) (StorageException "bad s3" {}))
                  services/commit-client (track calls :commit)
                  storage/update-status (track calls :update)]
      (try
        (sync-client test-app
                     test-config
                     test-tokens)
        (is false)
        (catch Exception error
          (is (:type (ex-data error))
              "StorageException")))
      (is (= 0 (count (:commit @calls))))
      (is (= 0 (count (:update @calls)))))))

; it should not handle errors
(deftest do-not-handle-errors
  (with-redefs [services/generate-new-client (throwing "ups")]
    (is (thrown? Exception (sync-client test-app
                                        test-config
                                        test-tokens)))))
