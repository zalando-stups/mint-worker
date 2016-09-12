(ns org.zalando.stups.mint.worker.external.bucket_storage
  (:require [clojure.string :as str]))

(defmacro StorageException
  [msg data]
  `(ex-info ~msg (merge ~data {:type "StorageException"})))

(defn storage-exception? [e]
  (= "StorageException" (:type (ex-data e))))

(defn infer-bucket-type
  "infer bucket type from bucket name"
  [bucket-name]
  (if (str/starts-with? bucket-name "gs://") :gs :s3))

(defmulti writable?
          "Check if a bucket is writable?"
          (fn [bucket-name app-id &] (infer-bucket-type bucket-name)))

(defmulti save-user
          "Save user credentials in bucket"
          (fn [bucket-name app-id username password &]
            (infer-bucket-type bucket-name)))

(defmulti save-client
          "Save client credentials in bucket"
          (fn [bucket-name app-id client-id client-secret &]
            (infer-bucket-type bucket-name)))
