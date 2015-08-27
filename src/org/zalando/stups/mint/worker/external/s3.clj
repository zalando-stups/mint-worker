(ns org.zalando.stups.mint.worker.external.s3
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io])
  (:import (java.io ByteArrayInputStream)
           (com.amazonaws AmazonServiceException)
           (com.amazonaws.services.s3 AmazonS3Client)
           (com.amazonaws.services.s3.model PutObjectRequest
                                            ObjectMetadata
                                            CannedAccessControlList)))

(def s3client (AmazonS3Client.))

(defmacro S3Exception
  [msg data]
  `(ex-info ~msg (merge ~data {:type "S3Exception"})))

(defn put-string
  "Stores an object in S3."
  [bucket-name path data]
  (let [bytes (-> data
                 (json/write-str)
                 (.getBytes "UTF-8"))
        stream (ByteArrayInputStream. bytes)
        metadata (doto  (ObjectMetadata.)
                   (.setContentLength (count bytes))
                   (.setContentType "application/json"))
        request (doto (PutObjectRequest. bucket-name path stream metadata)
                  (.withCannedAcl CannedAccessControlList/BucketOwnerFullControl))]
    (.putObject s3client request)))

(defn writable?
  [bucket app]
  (try
    (put-string bucket
                (str app "/test-mint-write")
                {:status "SUCCESS"})
    true
    (catch AmazonServiceException e
      false)))

(defn save-user [bucket-name app-id username password]
  (try
    (put-string bucket-name
                (str app-id "/user.json")
                {:application_username username
                 :application_password password})
    (catch AmazonServiceException e
      (S3Exception (.getMessage e)
                   {:status (.getStatusCode e)
                    :message (.getMessage e)
                    :original e}))))

(defn save-client [bucket-name app-id client-id client_secret]
  (try
    (put-string bucket-name
                (str app-id "/client.json")
                {:client_id client-id
                 :client_secret client_secret})
    (catch AmazonServiceException e
      (S3Exception (.getMessage e)
                   {:status (.getStatusCode e)
                    :message (.getMessage e)
                    :original e}))))
