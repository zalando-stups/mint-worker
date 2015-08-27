(ns org.zalando.stups.mint.worker.job.run-test
  (:require [clojure.test :refer :all]
            [org.zalando.stups.mint.worker.test-helpers :refer [throwing
                                                                track
                                                                third
                                                                test-tokens
                                                                test-config]]
            [org.zalando.stups.mint.worker.external.storage :as storage]
            [org.zalando.stups.mint.worker.job.sync-app :refer [sync-app]]
            [org.zalando.stups.mint.worker.job.run :as run]))

(def test-app
  {:id "kio"
   :s3_errors 0})

; test nothing bad happens without apps
(deftest resiliency-nothing
  (with-redefs [storage/list-apps (constantly [])]
    (run/run-sync test-config test-tokens)))

; test nothing bad happens on exception fetching apps
(deftest resiliency-error-on-fetch
  (with-redefs [storage/list-apps (throwing "mint-storage down")]
    (run/run-sync test-config test-tokens)))

; test nothing bad happens on exception processing an app
; test s3 counter does not get increased after non-s3 exception
(deftest resiliency-error-on-sync-app
  (let [calls (atom {})]
    (with-redefs [storage/list-apps (constantly (list test-app))
                  storage/update-status (track calls :update-status)
                  sync-app (throwing "error in sync-app")]
      (run/run-sync test-config test-tokens)
      (is (= (count (:update-status @calls))
             1))
      (let [call (first (:update-status @calls))
            app (second call)
            args (third call)]
        (is (= app (:id test-app)))
        (is (= (:has_problems args) true))
        (is (= (:s3_errors args) nil))))))

; test s3 counter gets increased after s3 exception
(deftest resiliency-s3-error-on-sync-app
  (let [calls (atom {})]
    (with-redefs [storage/list-apps (constantly (list test-app))
                  storage/update-status (track calls :update-status)
                  sync-app (throwing "error in sync-app" {:type "S3Exception"})]
      (run/run-sync test-config test-tokens)
      (is (= (count (:update-status @calls))
             1))
      (let [call (first (:update-status @calls))
            app (second call)
            args (third call)]
        (is (= app (:id test-app)))
        (is (= (:has_problems args) true))
        (is (= (:s3_errors args) 1))))))

