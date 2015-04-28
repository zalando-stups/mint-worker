(ns org.zalando.stups.mint.worker.util-test
  (:require [clojure.test :refer :all]
            [org.zalando.stups.mint.worker.util :refer :all]))

(deftest add-path-test

  (= (add-path "https://example.com/" "test") "https://example.com/test")
  (= (add-path "https://example.com/" "/test") "https://example.com/test")
  (= (add-path "https://example.com" "/test") "https://example.com/test")
  (= (add-path "https://example.com" "test") "https://example.com/test")
  (= (add-path "https://example.com/" "test" "123") "https://example.com/test/123")
  (= (add-path "https://example.com/" 123 "test/foo" "bar") "https://example.com/123/test/foo/bar")
  (= (add-path "https://example.com/" 123 "/test") "https://example.com/123/test"))
