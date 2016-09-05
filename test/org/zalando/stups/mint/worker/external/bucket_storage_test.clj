(ns org.zalando.stups.mint.worker.external.bucket_storage-test
  (:require [clojure.test :refer :all]
            [org.zalando.stups.mint.worker.external.bucket_storage :refer [infer-bucket-type]]))

; bucket type should be gs if prefixed with a gs:// scheme.
(deftest infer-bucket-type-gs
  (is (= (infer-bucket-type "gs://prefix-is-gs") :gs)))

; bucket type should be :s3 in all other cases.
(deftest infer-bucket-type-s3
  (is (= (infer-bucket-type "any-name-is-s3") :s3))
  (is (= (infer-bucket-type "s3://any-name-is-s3") :s3)))
