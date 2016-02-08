(ns org.zalando.stups.mint.worker.external.s3
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]
            [org.zalando.stups.friboo.log :as log]
            [clojure.string :as str])
  (:import (java.io ByteArrayInputStream)
           (com.amazonaws AmazonServiceException)
           (com.amazonaws.services.s3 AmazonS3Client)
           (com.amazonaws.services.s3.model PutObjectRequest
                                            ObjectMetadata
                                            CannedAccessControlList)
           (com.amazonaws.regions Regions Region)))

(defmacro S3Exception
  [msg data]
  `(ex-info ~msg (merge ~data {:type "S3Exception"})))

(defn infer-region [bucket-name]
  (reduce
    (fn [acc region]
      (if (.contains bucket-name (.getName region))
        (Region/getRegion region)
        acc))
    nil
    (Regions/values)))

(defn put-string
  "Stores an object in S3."
  [bucket-name path data]
  (let [region   (infer-region bucket-name)
        s3client (AmazonS3Client.)
        bytes    (-> data
                 (json/generate-string)
                 (.getBytes "UTF-8"))
        stream   (ByteArrayInputStream. bytes)
        metadata (doto (ObjectMetadata.)
                   (.setContentLength (count bytes))
                   (.setContentType "application/json"))
        request  (doto (PutObjectRequest. bucket-name path stream metadata)
                  (.withCannedAcl CannedAccessControlList/BucketOwnerFullControl))]
    ; to get rid of the S3V4AuthErrorRetryStrategy warnings
    (when region
      (.setRegion s3client region))
    (.putObject s3client request)))

(defn writable?
  [bucket-name app-id]
  {:pre [(not (str/blank? bucket-name))
         (not (str/blank? app-id))]}
  (try
    (put-string bucket-name
                (str app-id "/test-mint-write")
                {:status "SUCCESS"})
    (log/debug "S3 bucket %s with prefix %s is writable" bucket-name app-id)
    true
    (catch AmazonServiceException e
      (log/debug "S3 bucket %s with prefix %s is NOT WRITABLE. Reason %s." bucket-name app-id (str e))
      false)))

(defn save-user [bucket-name app-id username password]
  {:pre [(not (str/blank? bucket-name))
         (not (str/blank? app-id))]}
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
  {:pre [(not (str/blank? bucket-name))
         (not (str/blank? app-id))]}
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
