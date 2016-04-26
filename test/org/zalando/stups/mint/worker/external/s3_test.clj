(ns org.zalando.stups.mint.worker.external.s3-test
  (:require [clojure.test :refer :all]
            [org.zalando.stups.mint.worker.external.s3 :as s3])
  (:import (com.amazonaws AmazonServiceException)
           (com.amazonaws.regions Region Regions)))

(defn mock-put-error
  [& args]
  (throw (AmazonServiceException. "ohmigods")))

(deftest s3-writable-false
  (with-redefs [s3/put-string mock-put-error]
    (is (= (s3/writable? "bucket" "app")
           false))))

(deftest s3-writable-true
  (with-redefs [s3/put-string (constantly nil)]
    (is (= (s3/writable? "bucket" "app")
           true))))

(deftest s3-save-client-fail
  (with-redefs [s3/put-string mock-put-error]
    (let [error (s3/save-client "bucket" "app" "client" "secret")]
      (is (= (:type (ex-data error))
             "S3Exception")))))

(deftest s3-save-user-fail
  (with-redefs [s3/put-string mock-put-error]
    (let [error (s3/save-user "bucket" "app" "name" "password")]
      (is (= (:type (ex-data error))
             "S3Exception")))))

(deftest infer-ireland-region
  (is (= (s3/infer-region "a-mint-bucket-in-eu-west-1")
         (Region/getRegion (Regions/EU_WEST_1)))))

(deftest infer-nil-region
  (is (nil? (s3/infer-region "a-mint-bucket-with-no-region-hint"))))
