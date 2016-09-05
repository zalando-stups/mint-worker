(ns org.zalando.stups.mint.worker.external.bucket_storage)

(defmacro StorageException
  [msg data]
  `(ex-info ~msg (merge ~data {:type "StorageException"})))

(defn storage-exception? [e]
  (= "StorageException" (:type (ex-data e))))

; TODO: actually infer the bucket type
(defn infer-bucket-type
  "infer bucket type from bucket name"
  [bucket-name]
  (if (.startsWith bucket-name "gs://") :gs :s3))

(defmulti writable?
          "Check if a bucket is writable?"
          (fn [x] (infer-bucket-type (x :bucket-name))))

(defmulti save-user
          "Save user credentials in bucket"
          (fn [x] (infer-bucket-type (x :bucket-name))))

(defmulti save-client
          "Save client credentials in bucket"
          (fn [x] (infer-bucket-type (x :bucket-name))))
