(ns org.zalando.stups.mint.worker.external.etcd-test
  (:require [clojure.test :refer :all]
            [org.zalando.stups.mint.worker.external.http :as client]
            [org.zalando.stups.mint.worker.external.etcd :as etcd]))

(defn mock-put-error
  [& args]
  (throw (Exception. "ohmigods")))

(defn mock-success
  [& args]
  {:some "result"})

(deftest etcd-failure-false
  (with-redefs [client/put mock-put-error]
    (is (= (etcd/refresh-lock "url" "123" 600)
           false))))

(deftest etcd-success-true
  (with-redefs [client/put mock-success]
    (is (= (etcd/refresh-lock "url" "123" 600)
           true))))
