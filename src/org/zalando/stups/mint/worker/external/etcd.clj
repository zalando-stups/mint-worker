(ns org.zalando.stups.mint.worker.external.etcd
  (:require [clj-http.client :as client]
            [org.zalando.stups.friboo.log :as log]))

(defn refresh-lock
  "https://coreos.com/etcd/docs/latest/api.html#atomic-compare-and-swap"
  [etcd-lock-url value ttl]
  (try
     ; try "compare and swap" (last value must match current value)
     ; this call will fail (HTTP status != 2xx) if the old value is different or if no value exists!
     (let [result (:body (client/put etcd-lock-url
                   {:query-params {:prevValue value}
                    :form-params  {:value value :ttl ttl}
                    :as           :json}))]
          (log/debug "etcd returned %s" result)
          true)
     (catch Exception e
       (try
       ; fallback: try setting a new value
       ; this call will fail (HTTP status != 2xx) if a value already exists (somebody else acquired the lock)
       (let [result (:body (client/put etcd-lock-url
                   {:query-params {:prevExist false}
                    :form-params  {:value value :ttl ttl}
                    :as           :json}))]
            (log/debug "etcd returned %s" result)
            true)
       (catch Exception e
              (log/info "Failed to acquire/refresh lock for %s" value)
              false)))))
