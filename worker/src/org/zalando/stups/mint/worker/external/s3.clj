(ns org.zalando.stups.mint.worker.external.s3
  (:require [amazonica.aws.s3 :as s3]

            [clojure.data.json :as json]
            [clojure.java.io :as io])
  (:import (java.io ByteArrayInputStream)))

(defn put-string
  "Stores an object in S3."
  [bucket-name path data]
  (let [bytes (-> data
                 (json/write-str)
                 (.getBytes "UTF-8"))
        stream (-> bytes
                  (ByteArrayInputStream.))]
    (s3/put-object :bucket-name bucket-name
                   :key path
                   :metadata {:content-length (count bytes)}
                   :input-stream stream)))

(defn save-user [bucket-name app-id username password]
  (put-string bucket-name
              (str app-id "/user.json")
              {:application_username username
               :application_password password}))

(defn save-client [bucket-name app-id client-id client_secret]
  (put-string bucket-name
              (str app-id "/client.json")
              {:client_id client-id
               :client_secret client_secret}))
