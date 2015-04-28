(ns org.zalando.stups.mint.worker.util
  (:require [clojure.data.json :as json]
            [clj-http.lite.client :as client]))

(defn add-path
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

(defn fetch
  "GETs a JSON document and returns the parsed result."
  ([url]
   (-> (client/get url)
       :body
       json/read-str))
  ([url & path]
   (fetch (apply add-path url path))))

(defn fetch-with
  "GETs a JSON document and returns the parsed result."
  ([body method url]
   (-> (client/request {:method method :url url :body (when body (json/write-str body))})
       :body
       json/read-str))
  ([body method url & path]
   (fetch-with method body (apply add-path url path))))

