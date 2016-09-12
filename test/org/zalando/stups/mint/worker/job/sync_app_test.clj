(ns org.zalando.stups.mint.worker.job.sync-app-test
  (:require [clojure.test :refer :all]
            [org.zalando.stups.mint.worker.test-helpers :refer [throwing
                                                                third
                                                                track
                                                                sequentially
                                                                test-tokens
                                                                test-config]]
            [org.zalando.stups.mint.worker.external.storage :as storage]
            [org.zalando.stups.mint.worker.external.s3 :as s3]
            [org.zalando.stups.mint.worker.external.bucket_storage :refer [writable?]]
            [org.zalando.stups.mint.worker.job.sync-app :refer [sync-app]]
            [org.zalando.stups.mint.worker.job.sync-client :refer [sync-client]]
            [org.zalando.stups.mint.worker.job.sync-user :refer [sync-user]]
            [org.zalando.stups.mint.worker.job.sync-password :refer [sync-password]]))

(def test-app
  {:id        "kio"
   :s3_errors 0})

(def test-kio-app
  {:id "kio"
   :active true})

(def test-app-details
  {:id         "kio"
   :s3_buckets ["bucket_one" "bucket_two"]
   :s3_errors  0})

; it should not even try to test writability of buckets when max_errors is reached
(deftest do-nothing-when-max-errors-exceeded
  (let [calls (atom {})]
    (with-redefs [storage/get-app (track calls :mint-config)
                  writable? (track calls :writable)]
      (sync-app test-config
                (assoc test-app :s3_errors 11)
                test-kio-app
                test-tokens)
      (is (empty? (:mint-config @calls)))
      (is (empty? (:writable @calls))))))

; it should throw when at least one bucket is not writable
(deftest throw-when-no-writable-bucket
  (let [calls (atom {})]
    (with-redefs [writable? (sequentially true false)
                  storage/get-app (constantly test-app-details)
                  storage/update-status (track calls :update)]
      (try
        (sync-app test-config
                  test-app
                  test-kio-app
                  test-tokens)
        (is false)
        (catch Exception ex
          ; should not update status
          (is (= 0 (count (:update @calls)))))))))

; it should skip credentials rotation when app is inactive
(deftest skip-credentials-when-app-is-inactive
  (let [calls (atom {})
        kio-app {:id "kio"
                 :active false}]
    (with-redefs [storage/get-app (constantly test-app-details)
                  s3/put-string (constantly nil)
                  sync-user (constantly test-app)
                  sync-client (track calls :client)
                  sync-password (track calls :password)]
      (sync-app test-config
                test-app
                (assoc test-kio-app :active false)
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
                  s3/put-string (constantly nil)
                  sync-user (constantly test-app-details)
                  sync-client (track calls :client)
                  sync-password (track calls :password)]
      (sync-app test-config
                test-app
                test-kio-app
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
                  s3/put-string (constantly nil)
                  sync-user (constantly test-app-details)
                  sync-client (track calls :client)
                  sync-password (track calls :password)]
      (sync-app test-config
                test-app
                test-kio-app
                test-tokens)
      (is (= 1 (count (:client @calls))))
      (is (= 1 (count (:password @calls)))))))

; errors are handled by calling function
(deftest do-not-handle-errors
  (with-redefs [storage/get-app (throwing "ups")
                s3/put-string (constantly nil)]
    (is (thrown? Exception (sync-app test-config
                                     test-app
                                     test-kio-app
                                     test-tokens)))))
