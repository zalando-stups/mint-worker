(ns org.zalando.stups.mint.worker.job-test
  (:require [clojure.test :refer :all]
            [org.zalando.stups.mint.worker.job :as j]
            [org.zalando.stups.mint.worker.external.storage :as storage]
            [org.zalando.stups.mint.worker.external.apps :as apps]
            [org.zalando.stups.mint.worker.external.services :as services]))

; some utilities

(def test-config
  {:storage-url      "https://localhost"
   :kio-url          "https://localhost"
   :service-user-url "https://localhost"
   :prefix           "stups_"})

(defmacro always
  "Returns a function that accepts arbitrary parameters and always executes the same body. Similar to 'constantly' but
   with adhoc evaluation."
  [& body]
  `(fn [& args#]
     ~@body))

(defn lookup
  "Convinience mock function for getting app details."
  [lookup]
  (fn [_ app-id]
    (get lookup app-id)))

(defn track
  "Adds a tuple on call for an action."
  [a action]
  (fn [_ id]
    (swap! a conj [action id])))


;; resiliency (error tolerance of job)

(deftest resiliency-nothing
  ; no apps, nothing to do
  (with-redefs [storage/list-apps (always [])]
    (j/run-sync test-config)))

(deftest resiliency-list-unavailable
  ; error on getting list -> no bubble up, so job restarts in a imnute
  (with-redefs [storage/list-apps (always (throw (ex-info "this should be catched" {})))]
    (j/run-sync test-config)))

(deftest resiliency-individual-app
  ; error on processing on app-> no bubble up, so job restarts in a imnute
  (with-redefs [storage/list-apps (always [{:id "foo"}])
                storage/get-app (always (throw (ex-info "this should be catched" {})))]
    (j/run-sync test-config)))


;; synchronisation of users

(deftest sync-add-all-accs
  ; make sure, sync-app will be called for every account
  (let [calls (atom #{})]

    (with-redefs [storage/list-apps (always [{:id "foo"}, {:id "bar"}])
                  j/sync-app (track calls :sync-app)]

      (j/run-sync test-config)
      (is (= #{[:sync-app "foo"] [:sync-app "bar"]} @calls)))))

(deftest sync-delete-inactive-apps
  ; check for inactive apps
  (let [calls (atom #{})]

    (with-redefs [services/list-users (always #{"stups_foo"})
                  storage/get-app (lookup {"foo" {:id       "foo"
                                                  :username "stups_foo"}})
                  apps/get-app (lookup {"foo" {:id     "foo"
                                               :active false}})
                  services/delete-user (track calls :delete-service)]

      (j/sync-app test-config "foo")
      (is (= #{[:delete-service "stups_foo"]} @calls)))))

(deftest sync-delete-inactive-apps-ignore
  ; check for inactive apps
  (let [calls (atom #{})]

    (with-redefs [services/list-users (always #{})
                  storage/get-app (lookup {"foo" {:id       "foo"
                                                  :username "stups_foo"}})
                  apps/get-app (lookup {"foo" {:id     "foo"
                                               :active false}})
                  services/delete-user (track calls :delete-service)]

      (j/sync-app test-config "foo")
      (is (= #{} @calls)))))

(deftest sync-create-or-update-user
  ; check for inactive apps
  (let [calls (atom #{})]
    (with-redefs [services/list-users (always #{"stups_foo"})
                  storage/get-app (lookup {"foo" {:id       "foo"
                                                  :username "stups_foo"}})
                  apps/get-app (lookup {"foo" {:id     "foo"
                                               :active false}})
                  services/delete-user (track calls :delete-service)]
      (j/sync-app test-config "foo"))))

;; password and client secret rotation

(deftest password-rotation
  nil)
