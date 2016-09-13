(ns org.zalando.stups.mint.worker.job.sync-password-test
  (:require [clojure.test :refer :all]
            [clj-time.core :as time]
            [org.zalando.stups.mint.worker.job.sync-password :refer [sync-password]]
            [org.zalando.stups.mint.worker.test-helpers :refer [test-tokens
                                                                test-config
                                                                track
                                                                third
                                                                sequentially
                                                                throwing]]
            [org.zalando.stups.mint.worker.job.common :as c]
            [org.zalando.stups.mint.worker.external.services :as services]
            [org.zalando.stups.mint.worker.external.storage :as storage]
            [org.zalando.stups.mint.worker.external.bucket_storage :refer [save-user
                                                                           StorageException]]
            [org.zalando.stups.mint.worker.external.s3 :as s3]
            [org.zalando.stups.mint.worker.external.gcs :as gcs])
  (:import (com.amazonaws.services.s3.model PutObjectResult)))

(def test-app
  {:id "kio"
   :username "kio-user"
   :s3_buckets ["bucket-one" "bucket-two" "gs://bucket-three"]})

(def test-response
  {:password "admin"
   :txid "123"})

; should skip if last rotation <= 2 hours ago
(deftest should-skip-if-last-rotation-was-recently
  (let [recently (c/format-date-time (time/minus (time/now)
                                                 (time/hours 1)))
        test-app (assoc test-app :last_password_rotation recently)
        calls (atom {})]
    (with-redefs [services/generate-new-password (track calls :gen)]
      (sync-password test-app
                     test-config
                     test-tokens)
      (is (= 0 (count (:gen @calls)))))))

; should not skip if last rotation never happened
(deftest should-not-skip-if-never-rotated
  (let [calls (atom {})]
    (with-redefs [services/generate-new-password (constantly test-response)
                  s3/put-string (constantly (PutObjectResult.))
                  gcs/post-object (constantly nil)
                  services/commit-password (track calls :commit)
                  storage/update-status (track calls :update)]
      (sync-password test-app
                     test-config
                     test-tokens)
      (is (= 1 (count (:commit @calls))))
      (is (= 1 (count (:update @calls))))
      (let [args (first (:commit @calls))]
        ; signature: url user txid
        (is (= (second args)
               (:username test-app)))
        (is (= (third args)
               (:txid test-response)))))))

; should not skip if last rotation > 2 hours ago
(deftest should-not-skip-if-password-old
  (let [past (c/format-date-time (time/minus (time/now)
                                             (time/hours 5)))
        test-app (assoc test-app :last_password_rotation past)
        calls (atom {})]
    (with-redefs [services/generate-new-password (constantly test-response)
                  s3/put-string (constantly (PutObjectResult.))
                  gcs/post-object (constantly nil)
                  services/commit-password (track calls :commit)
                  storage/update-status (track calls :update)]
      (sync-password test-app
                     test-config
                     test-tokens)
      (is (= 1 (count (:commit @calls))))
      (is (= 1 (count (:update @calls)))))))

; should throw if a bucket is not writable
(deftest should-throw-if-bucket-unwritable
  (let [calls (atom {})]
    (with-redefs [services/generate-new-password (constantly test-response)
                  s3/put-string (sequentially (StorageException "bad s3" {}) (PutObjectResult.))
                  gcs/post-object (sequentially (StorageException "bad gcs" {}) nil)
                  services/commit-password (track calls :commit)
                  storage/update-status (track calls :update)]
      (try
        (sync-password test-app
                       test-config
                       test-tokens)
        (is false)
        (catch Exception error
          (is (:type (ex-data error))
              "StorageException")))
      (is (= 0 (count (:commit @calls))))
      (is (= 0 (count (:update @calls)))))))

; should not handle errors
(deftest should-not-handle-errors
  (let [calls (atom {})]
    (with-redefs [services/generate-new-password (throwing "whoopsie daisy")
                  services/commit-password (track calls :commit)
                  storage/update-status (track calls :update)]
      (is (thrown? Exception (sync-password test-app
                                            test-config
                                            test-tokens)))
      (is (= 0 (count (:commit @calls))))
      (is (= 0 (count (:update @calls)))))))
