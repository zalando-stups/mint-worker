(ns org.zalando.stups.mint.worker.external.gcs-test
  (:require [clojure.test :refer :all]
            [org.zalando.stups.mint.worker.external.gcs :as gcs]
            [org.zalando.stups.mint.worker.external.bucket_storage :refer [save-client
                                                                           save-user
                                                                           writable?]]
            [org.zalando.stups.mint.worker.test-helpers :refer [test-tokens
                                                                test-config]]))

(defn mock-put-error
  [& args]
  (throw (Exception. "ohmigods")))

(deftest mint-coworker-writable-false
  (with-redefs [gcs/post-object mock-put-error]
    (is (= (writable? "gs://bucket" "app" test-config test-tokens)
           false))))

(deftest mint-coworker-writable-false
  (with-redefs [gcs/post-object (constantly nil)]
    (is (= (writable? "gs://bucket" "app" test-config test-tokens)
           true))))

(deftest mint-coworker-save-client-fail
 (with-redefs [gcs/post-object mock-put-error]
   (let [error (save-client "gs://bucket" "app" "client" "secret"
                            test-config test-tokens)]
     (is (= (:type (ex-data error))
            "StorageException")))))

(deftest mint-coworker-save-client-success
  (with-redefs [gcs/post-object (constantly nil)]
   (is (nil? (save-client "gs://bucket" "app" "client" "secret"
                          test-config test-tokens)))))

(deftest mint-coworker-save-user-fail
 (with-redefs [gcs/post-object mock-put-error]
   (let [error (save-user "gs://bucket" "app" "name" "password"
                          test-config test-tokens)]
     (is (= (:type (ex-data error))
            "StorageException")))))

(deftest mint-coworker-save-user-success
  (with-redefs [gcs/post-object (constantly nil)]
   (is (nil? (save-client "gs://bucket" "app" "name" "password"
                          test-config test-tokens)))))
