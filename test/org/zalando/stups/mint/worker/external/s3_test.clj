(ns org.zalando.stups.mint.worker.external.s3-test
  (:require [clojure.test :refer :all]
            [org.zalando.stups.mint.worker.external.s3 :as s3])
  (:import (com.amazonaws AmazonServiceException)))

(defn mock-put-error
  [& args]
  (throw (AmazonServiceException. "ohmigods")))

(deftest s3-writable-false
  (with-redefs
    [s3/put-string mock-put-error]
      (is (= (s3/writable? "test-bucket" "test-app")
             false))))

(deftest s3-writable-true
  (with-redefs
    [s3/put-string (constantly nil)]
      (is (= (s3/writable? "test-bucket" "test-app")
             true))))