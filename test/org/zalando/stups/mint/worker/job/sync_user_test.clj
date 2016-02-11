(ns org.zalando.stups.mint.worker.job.sync-user-test
  (:require [clojure.test :refer :all]
            [org.zalando.stups.mint.worker.job.sync-user :refer [sync-user]]
            [org.zalando.stups.mint.worker.test-helpers :refer [test-tokens
                                                                test-config
                                                                throwing
                                                                sequentially
                                                                track]]
            [clj-time.core :as time]
            [org.zalando.stups.mint.worker.job.common :as c]
            [org.zalando.stups.mint.worker.external.services :as services]
            [org.zalando.stups.mint.worker.external.storage :as storage]
            [org.zalando.stups.mint.worker.external.s3 :as s3])
  (:import (com.amazonaws.services.s3.model PutObjectResult)))

(def test-app
  {:id "kio"
   :username "kio-user"
   :s3_buckets ["bucket-one" "bucket-two"]})

(def test-kio-app
  {:id "kio"
   :team_id "stups"
   :active true})

(def test-response
  {:client_id "kio-client"})

; inactive app without user -> skip
(deftest should-skip-if-inactive-without-user
  (let [test-kio-app (assoc test-kio-app :active false)
        calls (atom {})]
    (with-redefs [services/list-users (constantly [])
                  services/delete-user (track calls :delete)
                  services/create-or-update-user (track calls :update)]
      (sync-user test-app
                 test-kio-app
                 test-config
                 test-tokens)
      (is (= 0 (count (:delete @calls))))
      (is (= 0 (count (:update @calls)))))))

; inactive app with user -> delete user
(deftest should-delete-if-inactive-with-user
  (let [test-kio-app (assoc test-kio-app :active false)
        calls (atom {})]
    (with-redefs [services/list-users (constantly [{:name "Some test application"
                                                    :id (:username test-app)}])
                  services/delete-user (track calls :delete)
                  services/create-or-update-user (track calls :update)]
      (sync-user test-app
                 test-kio-app
                 test-config
                 test-tokens)
      ; 2 => one for primary, one for secondary
      (is (= 2 (count (:delete @calls))))
      (is (= 0 (count (:update @calls))))
      (let [args (first (:delete @calls))]
        (is (= (second args)
               (:username test-app)))))))

; should skip if last_synced >= last_modified
(deftest should-skip-if-synced-equals-modified
  (let [now (time/now)
        test-app (assoc test-app :last_modified (c/format-date-time now)
                                 :last_synced (c/format-date-time now))
        calls (atom {})]
    (with-redefs [services/create-or-update-user (track calls :update)]
      (sync-user test-app
                 test-kio-app
                 test-config
                 test-tokens)
      (is (= 0 (count (:update @calls)))))))

(deftest should-skip-if-synced-greater-modified
  (let [now (time/now)
        test-app (assoc test-app :last_modified (c/format-date-time now)
                                 :last_synced (c/format-date-time (time/plus now
                                                                             (time/minutes 3))))
        calls (atom {})]
    (with-redefs [services/create-or-update-user (track calls :update)]
      (sync-user test-app
                 test-kio-app
                 test-config
                 test-tokens)
      (is (= 0 (count (:update @calls)))))))

; should not skip if last_synced is null
(deftest should-not-skip-if-not-synced
  (let [test-app (assoc test-app :is_client_confidential true)
        calls (atom {})]
    (with-redefs [services/create-or-update-user (fn [& args]
                                                   (apply (track calls :update-user) args)
                                                   test-response)
                  storage/update-status (track calls :update-status)]
      (let [returned-app (sync-user test-app
                                    test-kio-app
                                    test-config
                                    test-tokens)]
        (is (:client_id returned-app))
        ; 2 => one for primary, one for secondary
        (is (= 2 (count (:update-user @calls))))
        (is (= 1 (count (:update-status @calls))))))))

; should sync client_id if not confidential and not available
(deftest should-sync-if-not-confidential
  (let [test-app (assoc test-app :is_client_confidential false)
        calls (atom {})]
    (with-redefs [services/create-or-update-user (constantly test-response)
                  s3/save-client (constantly (PutObjectResult.))
                  storage/update-status (track calls :update)]
      (let [app (sync-user test-app
                           test-kio-app
                           test-config
                           test-tokens)]
        (is (= (:client_id app)
               (:client_id test-response)))
        (is (= 1 (count (:update @calls))))))))

; should not update and throw on s3 error
(deftest should-not-update-and-throw-on-s3-error
  (let [test-app (assoc test-app :is_client_confidential false)
        calls (atom {})]
    (with-redefs [services/create-or-update-user (constantly test-response)
                  s3/save-client (sequentially (PutObjectResult.) (s3/S3Exception "bad s3" {}))
                  storage/update-status (track calls :update)]
      (try
        (sync-user test-app
                   test-kio-app
                   test-config
                   test-tokens)
        (catch Exception error
          (is (= (:type (ex-data error))
                 "S3Exception"))
          (is (= 0 (count (:update @calls)))))))))
