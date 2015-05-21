(ns org.zalando.stups.mint.worker.external.s3
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io])
  (:import (java.io ByteArrayInputStream)
           (com.amazonaws.services.s3 AmazonS3Client)
           (com.amazonaws.services.s3.model PutObjectRequest ObjectMetadata CannedAccessControlList)))

(def s3client (AmazonS3Client.))

(defn put-string
  "Stores an object in S3."
  [bucket-name path data]
  (let [bytes (-> data
                 (json/write-str)
                 (.getBytes "UTF-8"))
        stream (-> bytes
                  (ByteArrayInputStream.))
        metadata (doto  (ObjectMetadata.)
                   (.setContentLength (count bytes))
                   (.setContentType "application/json"))
        request (doto (PutObjectRequest. bucket-name path stream metadata)
                  (.withCannedAcl CannedAccessControlList/BucketOwnerRead))]
    (.putObject s3client request)))

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
