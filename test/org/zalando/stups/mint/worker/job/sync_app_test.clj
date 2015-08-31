(ns org.zalando.stups.mint.worker.job.sync-app-test
  (:require [clojure.test :refer :all]
            [org.zalando.stups.mint.worker.test-helpers :refer [throwing
                                                                third
                                                                track
                                                                sequentially
                                                                test-tokens
                                                                test-config]]
            [org.zalando.stups.mint.worker.external.apps :as apps]
            [org.zalando.stups.mint.worker.external.storage :as storage]
            [org.zalando.stups.mint.worker.external.s3 :as s3]
            [org.zalando.stups.mint.worker.job.sync-app :refer [sync-app]]
            [org.zalando.stups.mint.worker.job.sync-client :refer [sync-client]]
            [org.zalando.stups.mint.worker.job.sync-user :refer [sync-user]]
            [org.zalando.stups.mint.worker.job.sync-password :refer [sync-password]]))

(def test-app
  {:id        "kio"
   :s3_errors 0})

(def test-app-details
  {:id         "kio"
   :s3_buckets ["bucket_one" "bucket_two"]
   :s3_errors  0})

; it should not even try to test writability of buckets when max_errors is reached
(deftest do-nothing-when-max-errors-exceeded
  (let [calls (atom {})]
    (with-redefs [storage/get-app (track calls :mint-config)
                  s3/writable? (track calls :writable)]
      (sync-app test-config
                (assoc test-app :s3_errors 11)
                test-tokens)
      (is (empty? (:mint-config @calls)))
      (is (empty? (:writable @calls))))))

; it should increase s3_errors when at least one bucket is not writable
(deftest inc-s3-errors-when-no-writable-bucket
  (let [calls (atom {})]
    (with-redefs [s3/writable? (sequentially true false)
                  storage/get-app (constantly test-app-details)
                  storage/update-status (track calls :update)
                  apps/get-app (track calls :apps)]
      (sync-app test-config
                test-app
                test-tokens)
      ; did not try to fetch app from kio
      (is (= 0 (count (:apps @calls))))
      ; updates status in storage
      (is (= 1 (count (:update @calls))))
      (let [call (first (:update @calls))
            app (second call)
            args (third call)]
        (is (= app (:id test-app)))
        (is (= 1 (:s3_errors args)))))))

; it should skip credentials rotation when app is inactive
(deftest skip-credentials-when-app-is-inactive
  (let [calls (atom {})
        kio-app {:id "kio"
                 :active false}]
    (with-redefs [storage/get-app (constantly test-app-details)
                  s3/writable? (constantly true)
                  apps/get-app (constantly kio-app)
                  sync-user (constantly test-app)
                  sync-client (track calls :client)
                  sync-password (track calls :password)]
      (sync-app test-config
                test-app
                test-tokens)
      (is (= 0 (count (:client @calls))))
      (is (= 0 (count (:password @calls)))))))

; it should skip client rotation when client is not confidential
(deftest skip-client-rotation-when-not-confidential
  (let [calls (atom {})
        test-app-details (assoc test-app-details :is_client_confidential false)
        kio-app {:id "kio"
                 :active true}]
    (with-redefs [storage/get-app (constantly test-app-details)
                  s3/writable? (constantly true)
                  apps/get-app (constantly kio-app)
                  sync-user (constantly test-app-details)
                  sync-client (track calls :client)
                  sync-password (track calls :password)]
      (sync-app test-config
                test-app
                test-tokens)
      (is (= 0 (count (:client @calls))))
      (is (= 1 (count (:password @calls)))))))

; it should do everything when client is confidential
(deftest do-everything-when-confidential
  (let [calls (atom {})
        test-app-details (assoc test-app :is_client_confidential true)
        kio-app {:id "kio"
                 :active true}]
    (with-redefs [storage/get-app (constantly test-app-details)
                  s3/writable? (constantly true)
                  apps/get-app (constantly kio-app)
                  sync-user (constantly test-app-details)
                  sync-client (track calls :client)
                  sync-password (track calls :password)]
      (sync-app test-config
                test-app
                test-tokens)
      (is (= 1 (count (:client @calls))))
      (is (= 1 (count (:password @calls)))))))

; errors are handled by calling function
(deftest do-not-handle-errors
  (with-redefs [storage/get-app (constantly test-app-details)
                s3/writable? (constantly true)
                apps/get-app (throwing "ups")]
    (is (thrown? Exception (sync-app test-config
                                     test-app
                                     test-tokens)))))
