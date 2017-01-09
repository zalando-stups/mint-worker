(ns org.zalando.stups.mint.worker.external.http
  (:require [clj-http.client :as client]))

(def ^:private default-http-options
  {:socket-timeout 10000
   :conn-timeout 10000})

(defn get [url & [options]]
  (client/get url (merge default-http-options (or options {}))))

(defn post [url & [options]]
  (client/post url (merge default-http-options (or options {}))))

(defn patch [url & [options]]
  (client/patch url (merge default-http-options (or options {}))))

(defn put [url & [options]]
  (client/put url (merge default-http-options (or options {}))))

(defn delete [url & [options]]
  (client/delete url (merge default-http-options (or options {}))))
