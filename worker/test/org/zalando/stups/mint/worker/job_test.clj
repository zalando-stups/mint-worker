(ns org.zalando.stups.mint.worker.job-test
  (:require [clojure.test :refer :all]
            [org.zalando.stups.mint.worker.job :as j]
            [org.zalando.stups.mint.worker.external.storage :as storage]))

(def test-config
  {:storage-url "https://localhost"})

(defmacro always
  "Returns a function that accepts arbitrary parameters and always executes the same body."
  [& body]
  `(fn [& _#]
     ~@body))

(deftest resiliency

  ; no apps, nothing to do
  (with-redefs [storage/get-managed-apps (always [])]
    (j/run-sync test-config))

  ; error on getting list -> no bubble up, so job restarts in a imnute
  (with-redefs [storage/get-managed-apps (always (throw (ex-info "this should be catched" {})))]
    (j/run-sync test-config))

  ; error on processing on app-> no bubble up, so job restarts in a imnute
  (with-redefs [storage/get-managed-apps (always [{:id 123}])
                storage/get-app-details (always (throw (ex-info "this should be catched" {})))]
    (j/run-sync test-config)))

