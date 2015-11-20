(ns org.zalando.stups.mint.worker.job.run-test
  (:require [clojure.test :refer :all]
            [org.zalando.stups.mint.worker.test-helpers :refer [throwing
                                                                track
                                                                third
                                                                test-tokens
                                                                test-config]]
            [org.zalando.stups.mint.worker.external.apps :as apps]
            [org.zalando.stups.mint.worker.external.s3 :as s3]
            [org.zalando.stups.mint.worker.external.storage :as storage]
            [org.zalando.stups.mint.worker.job.sync-app :refer [sync-app]]
            [org.zalando.stups.mint.worker.job.run :as run]))

(def test-app
  {:id "kio"
   :s3_buckets ["test-bucket"]
   :s3_errors 0})

(def test-kio-app
  {:id "kio"
   :active true})

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
    (with-redefs [apps/list-apps (constantly (list test-kio-app))
                  storage/list-apps (constantly (list test-app))
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

; https://github.com/zalando-stups/mint-worker/issues/16
(deftest increase-s3-errors-when-unwritable-buckets
  (let [calls (atom {})]
    (with-redefs [apps/list-apps (constantly (list test-kio-app))
                  storage/list-apps (constantly (list test-app))
                  storage/get-app (constantly test-app)
                  s3/writable? (constantly false)
                  storage/update-status (track calls :update-status)]
      (run/run-sync test-config test-tokens)
      (is (= (count (:update-status @calls))
             1))
      (let [call (first (:update-status @calls))
            app (second call)
            args (third call)]
        (is (= app (:id test-app)))
        (is (= (:has_problems args) true))
        (is (= (:s3_errors args) 1))))))

(deftest use-correct-kio-app
  (let [calls (atom {})]
    (with-redefs [apps/list-apps (constantly (list test-kio-app))
                  storage/list-apps (constantly (list test-app))
                  storage/update-status (constantly nil)
                  sync-app (track calls :sync)]
      (run/run-sync test-config test-tokens)
      (is (= (count (:sync @calls))
             1))
      (let [call-param (first (:sync @calls))
            kio-app (third call-param)]
        (is (= test-kio-app
               kio-app))))))
