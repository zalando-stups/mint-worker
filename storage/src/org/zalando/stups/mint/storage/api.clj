; Copyright 2015 Zalando SE
;
; Licensed under the Apache License, Version 2.0 (the "License")
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
;
;     http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.

(ns org.zalando.stups.mint.storage.api
  (:require [org.zalando.stups.friboo.system.http :refer [def-http-component]]
            [org.zalando.stups.mint.storage.sql :as sql]
            [ring.util.response :refer :all]
            [org.zalando.stups.friboo.ring :refer :all]
            [org.zalando.stups.friboo.log :as log]))

; define the API component and its dependencies
(def-http-component API "api/mint-api.yaml" [db])

(def default-http-configuration
  {:http-port 8080})

;; credentials

(defn read-credentials
  "Returns all credentials."
  [_ _ db]
  (log/debug "Listing all credentials...")
  (-> (sql/read-credentials {} {:connection db})
      (response)
      (content-type-json)))

(defn read-credential
  "Returns credentials for an application."
  [parameters _ db]
  (log/debug "Reading credentials for %s..." parameters)
  (-> (sql/read-credential parameters {:connection db})
      (single-response)
      (content-type-json)))

(defn read-credential-sensitive
  "Returns sensitive credentials for an application."
  [parameters _ db]
  (log/debug "Reading sensitive credentials for %s..." parameters)
  (-> (sql/read-credential-sensitive parameters {:connection db})
      (single-response)
      (content-type-json)))
