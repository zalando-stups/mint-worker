(ns org.zalando.stups.mint.worker.external.s3
  (:require [amazonica.aws.s3 :as s3]
            [amazonica.aws.s3transfer :as s3t]
            [clojure.data.json :as json]
            [clojure.java.io :as io]))

(defn save-user [bucket-name app-id username password]
  (s3/put-object :bucket-name bucket-name
                 :key (str app-id "/user.json")
                 :input-stream (-> {:application_username username :application_password password}
                                   json/write-str
                                   .getBytes
                                   io/input-stream)))

(defn save-client [bucket-name app-id client-id client_secret]
  (s3/put-object :bucket-name bucket-name
                 :key (str app-id "/client.json")
                 :input-stream (-> {:client_id client-id :client_secret client_secret}
                                   json/write-str
                                   .getBytes
                                   io/input-stream)))
