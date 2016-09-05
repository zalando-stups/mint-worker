(ns org.zalando.stups.mint.worker.external.s3-test
  (:require [clojure.test :refer :all]
            [org.zalando.stups.mint.worker.external.s3 :as s3]
            [org.zalando.stups.mint.worker.external.bucket_storage :refer [save-client
                                                                           save-user
                                                                           writable?]])
  (:import (com.amazonaws AmazonServiceException)
           (com.amazonaws.regions Region Regions)))

(defn mock-put-error
  [& args]
  (throw (AmazonServiceException. "ohmigods")))

(deftest s3-writable-false
  (with-redefs [s3/put-string mock-put-error]
    (is (= (writable? {:bucket-name "bucket", :app-id "app"})
           false))))

(deftest s3-writable-true
  (with-redefs [s3/put-string (constantly nil)]
    (is (= (writable? {:bucket-name "bucket", :app-id "app"})
           true))))

(deftest s3-save-client-fail
  (with-redefs [s3/put-string mock-put-error]
    (let [error (save-client {:bucket-name "bucket"
                              :app-id "app"
                              :client-id "client"
                              :client-secret"secret"})]
      (is (= (:type (ex-data error))
             "StorageException")))))

(deftest s3-save-client-success
  (with-redefs [s3/put-string (constantly nil)]
    (is (nil? (save-client {:bucket-name "bucket"
                            :app-id "app"
                            :client-id "client"
                            :client-secret"secret"})))))

(deftest s3-save-user-fail
  (with-redefs [s3/put-string mock-put-error]
    (let [error (save-user {:bucket-name "bucket"
                            :app-id "app"
                            :name "name"
                            :password "password"})]
      (is (= (:type (ex-data error))
             "StorageException")))))

(deftest infer-ireland-region
  (is (= (s3/infer-region "a-mint-bucket-in-eu-west-1")
         (Region/getRegion (Regions/EU_WEST_1)))))

(deftest infer-nil-region
  (is (nil? (s3/infer-region "a-mint-bucket-with-no-region-hint"))))
