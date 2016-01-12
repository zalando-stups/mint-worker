(ns org.zalando.stups.mint.worker.external.etcd
  (:require [clj-http.client :as client]
            [org.zalando.stups.friboo.log :as log]))

(defn refresh-lock
  "https://coreos.com/etcd/docs/latest/api.html#atomic-compare-and-swap"
  [etcd-lock-url value ttl]
  (try
     (let [result (:body (client/put etcd-lock-url
                   {:content-type :json
                    :query-params {:prevValue value}
                    :form-params  {:value value :ttl ttl}
                    :as           :json}))]
          (log/debug "etcd returned %s" result)
          true)
     (catch Exception e
       (try
       (let [result (:body (client/put etcd-lock-url
                   {:content-type :json
                    :query-params {:prevExist false}
                    :form-params  {:value value :ttl ttl}
                    :as           :json}))]
            (log/debug "etcd returned %s" result)
            true)
       (catch Exception e
              (log/info "Failed to acquire/refresh lock %s" value)
              false)))))
