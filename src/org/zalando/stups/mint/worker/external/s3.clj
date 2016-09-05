(ns org.zalando.stups.mint.worker.external.s3
  (:require [cheshire.core :as json]
            [org.zalando.stups.friboo.log :as log]
            [clojure.string :as str]
            [org.zalando.stups.mint.worker.external.bucket_storage :refer [writable?
                                                                           save-user
                                                                           save-client
                                                                           StorageException]])
  (:import (java.io ByteArrayInputStream)
           (com.amazonaws AmazonServiceException)
           (com.amazonaws.services.s3 AmazonS3Client)
           (com.amazonaws.services.s3.model PutObjectRequest
                                            ObjectMetadata
                                            CannedAccessControlList)
           (com.amazonaws.regions Regions Region)))

(defn infer-region [bucket-name]
  (some->>
    (Regions/values)
    (filter #(-> bucket-name (.contains (-> % .getName))))
    first
    Region/getRegion))

(defn put-string
  "Stores an object in S3."
  [bucket-name path data]
  (let [region (infer-region bucket-name)
        s3client (AmazonS3Client.)
        bytes (-> data
                  (json/generate-string)
                  (.getBytes "UTF-8"))
        stream (ByteArrayInputStream. bytes)
        metadata (doto (ObjectMetadata.)
                   (.setContentLength (count bytes))
                   (.setContentType "application/json"))
        request (doto (PutObjectRequest. bucket-name path stream metadata)
                  (.withCannedAcl CannedAccessControlList/BucketOwnerFullControl))]
    ; to get rid of the S3V4AuthErrorRetryStrategy warnings
    (when region
      (.setRegion s3client region))
    (.putObject s3client request)))

(defmethod writable? :s3 [params]
  {:pre [(not (str/blank? (params :bucket-name)))
         (not (str/blank? (params :app-id)))]}
  (let [bucket-name (params :bucket-name)
        app-id (params :app-id)]
    (try
      (put-string bucket-name
                  (str app-id "/test-mint-write")
                  {:status "SUCCESS"})
      (log/debug "S3 bucket %s with prefix %s is writable" bucket-name app-id)
      true
      (catch AmazonServiceException e
        (log/debug "S3 bucket %s with prefix %s is NOT WRITABLE. Reason %s." bucket-name app-id (str e))
        false))))

(defmethod save-user :s3 [params]
  {:pre [(not (str/blank? (params :bucket-name)))
         (not (str/blank? (params :app-id)))]}
  (let [bucket-name (params :bucket-name)
        app-id (params :app-id)
        username (params :username)
        password (params :password)]
    (try
      (put-string bucket-name
                  (str app-id "/user.json")
                  {:application_username username
                   :application_password password})
      (catch AmazonServiceException e
        (StorageException (.getMessage e)
                          {:status   (.getStatusCode e)
                           :message  (.getMessage e)
                           :original e})))))

(defmethod save-client :s3 [params]
  {:pre [(not (str/blank? (params :bucket-name)))
         (not (str/blank? (params :app-id)))]}
  (let [bucket-name (params :bucket-name)
        app-id (params :app-id)
        client-id (params :client-id)
        client-secret (params :client-secret)]
    (try
      (put-string bucket-name
                  (str app-id "/client.json")
                  {:client_id     client-id
                   :client_secret client-secret})
      (catch AmazonServiceException e
        (StorageException (.getMessage e)
                          {:status   (.getStatusCode e)
                           :message  (.getMessage e)
                           :original e})))))
