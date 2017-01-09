(ns org.zalando.stups.mint.worker.external.gcs
  (:require [org.zalando.stups.friboo.ring :refer [conpath]]
            [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.friboo.config :as config]
            [clojure.string :as str]
            [org.zalando.stups.mint.worker.external.http :as client]
            [org.zalando.stups.mint.worker.external.bucket_storage :refer [writable?
                                                                           save-user
                                                                           save-client
                                                                           StorageException]]
            [org.zalando.stups.friboo.system.oauth2 :as oauth2]))

(defn post-object
  "POST /{bucket}/{path}"
  [mint-coworker-url bucket-name path data tokens]
  (client/post (conpath mint-coworker-url bucket-name path)
               {:oauth-token  (oauth2/access-token :mint-coworker-w-api tokens)
                :content-type :json
                :form-params  data
                :as           :json}))

(defmethod writable? :gs [bucket-name app-id configuration tokens]
  {:pre [(not (str/blank? bucket-name))
         (not (str/blank? app-id))]}
  (let [mint-coworker-url (config/require-config configuration :mint-coworker-url)
        bucket-name (str/replace bucket-name #"^gs://" "")]
    (try
      (post-object mint-coworker-url
                   bucket-name
                   (str app-id "/test-mint-write")
                   {:status "SUCCESS"}
                   tokens)
      (log/debug "Bucket %s with prefix %s is writable" bucket-name app-id)
      true
      (catch Exception e
        (log/debug "Bucket %s with prefix %s is NOT WRITABLE. Reason %s." bucket-name app-id (str e))
        false))))

(defmethod save-user :gs [bucket-name app-id username password configuration tokens]
  {:pre [(not (str/blank? bucket-name))
         (not (str/blank? app-id))]}
  (let [mint-coworker-url (config/require-config configuration :mint-coworker-url)
        bucket-name (str/replace bucket-name #"^gs://" "")]
    (try
      (post-object mint-coworker-url
                   bucket-name
                   (str app-id "/user.json")
                   {:application_username username
                    :application_password password}
                   tokens)
      (catch Exception e
        (StorageException (.getMessage e)
                     {:message (.getMessage e)
                      :original e})))))

(defmethod save-client :gs [bucket-name app-id client-id client-secret configuration tokens]
  {:pre [(not (str/blank? bucket-name))
         (not (str/blank? app-id))]}
  (let [mint-coworker-url (config/require-config configuration :mint-coworker-url)
        bucket-name (str/replace bucket-name #"^gs://" "")]
    (try
      (post-object mint-coworker-url
                   bucket-name
                   (str app-id "/client.json")
                   {:client_id client-id
                    :client_secret client-secret}
                   tokens)
      (catch Exception e
        (StorageException (.getMessage e)
                     {:message (.getMessage e)
                      :original e})))))
