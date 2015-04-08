(ns org.zalando.stups.mint.worker.jobs
  (:require [org.zalando.stups.friboo.system.cron :refer [def-cron-component]]
            [org.zalando.stups.friboo.log :as log]
            [overtone.at-at :refer [every]]
            [clj-http.lite.client :as client]
            [clojure.data.json :as json]))

(def default-configuration
  {:jobs-cpu-count        1
   :jobs-every-ms         10000
   :jobs-initial-delay-ms 1000})

(defn- add-path
  "Concatenates path elements to an URL."
  [url & path]
  (let [[x & xs] path]
    (if x
      (let [url (if (or
                      (.endsWith url "/")
                      (.startsWith x "/"))
                  (str url x)
                  (str url "/" x))]
        (recur url xs))
      url)))

(defn- fetch-url
  "GETs a JSON document and returns the parsed result."
  ([url]
   (-> (client/get url)
       :body
       json/read-str))
  ([url path]
   (fetch-url (add-path url path))))

(defn fetch-apps
  "Fetches list of all applications."
  [kio-url]
  ; TODO filter only active
  (fetch-url kio-url "/apps"))

(defn- sync
  "Creates and deletes applications, rotates and distributes their credentials."
  [configuration]
  (let [{:keys [kio-url storage-url]} configuration]
    (log/info "Starting new sync run with %s..." kio-url)
    (try
      (doseq [app (fetch-apps kio-url)]
        ; TODO do magic
        )
      (catch Exception e
        (log/error e "Could not fetch apps %s." (str e))))))

(def-cron-component
  Jobs []

  (let [{:keys [every-ms initial-delay-ms]} configuration]

    (every every-ms (partial sync configuration) pool :initial-delay initial-delay-ms :desc "synchronisation")))
