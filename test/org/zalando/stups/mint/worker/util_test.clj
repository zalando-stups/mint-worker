(ns org.zalando.stups.mint.worker.util-test
  (:require [clojure.test :refer :all]
            [org.zalando.stups.mint.worker.util :refer :all]))

(deftest conpath-test

  (is (= (conpath "https://example.com/" "test")
         "https://example.com/test"))
  (is (= (conpath "https://example.com/" "/test")
         "https://example.com/test"))
  (is (= (conpath "https://example.com" "/test")
         "https://example.com/test"))
  (is (= (conpath "https://example.com" "test")
         "https://example.com/test"))
  (is (= (conpath "https://example.com/" "test" "123")
         "https://example.com/test/123"))
  (is (= (conpath "https://example.com/" "123" "test/foo" "bar")
         "https://example.com/123/test/foo/bar"))
  (is (= (conpath "https://example.com/" "123" "/test")
         "https://example.com/123/test")))
