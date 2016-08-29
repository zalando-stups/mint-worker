(ns org.zalando.stups.mint.worker.external.bucket_storage)

(defmacro StorageException
  [msg data]
  `(ex-info ~msg (merge ~data {:type "StorageException"})))

(defprotocol BucketStorage
  "Storage defines a protocol for writing user and client credentials to a bucket storage service like S3 or GCS"
  (writable? [_ bucket-name app-id] "Check if bucket is writeable")
  (save-user [_ bucket-name app-id username password] "Save user credentials in bucket")
  (save-client [_ bucket-name app-id client-id client_secret] "Save client credentials in bucket"))
