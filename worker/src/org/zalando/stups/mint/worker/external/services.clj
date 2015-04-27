(ns org.zalando.stups.mint.worker.external.services
  (:require [org.zalando.stups.mint.worker.util :as util]))

(defn list-users
  "GET /services

   #{'user1', 'user2'}"
  [service-user-url]
  (into #{}
    (util/fetch service-user-url "/services")))

(defn delete-user
  [service-user-url username]
  (util/fetch-with :delete service-user-url "/services/" username))
