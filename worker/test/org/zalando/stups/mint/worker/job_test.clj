(ns org.zalando.stups.mint.worker.job-test
  (:require [clojure.test :refer :all]
            [org.zalando.stups.mint.worker.job :as j]
            [org.zalando.stups.mint.worker.external.storage :as storage]
            [org.zalando.stups.mint.worker.external.apps :as apps]
            [org.zalando.stups.mint.worker.external.services :as services]
            [clj-time.core :as time]))

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
  ([a action]
   (fn [_ id]
     (swap! a conj [action id])))
  ([a action tracked-args]
   (fn [& all-args]
     (swap! a conj [action (into [] (map #(nth all-args %) tracked-args))]))))


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
  ; error on processing on app-> no bubble up, so job restarts in a minute
  (with-redefs [storage/list-apps (always [{:id "foo"}])
                storage/get-app (always (throw (ex-info "this should be catched" {})))]
    (j/run-sync test-config)))


;; synchronisation of apps

(deftest sync-add-all-accs
  ; make sure, sync-app will be called for every account
  (let [calls (atom #{})]

    (with-redefs [storage/list-apps (always [{:id "foo"}, {:id "bar"}])
                  j/sync-app (track calls :sync-app)]

      (j/run-sync test-config)
      (is (= #{[:sync-app "foo"] [:sync-app "bar"]} @calls)))))

(def foo-app {:id            "foo"
              :username      "stups_foo"
              :last_modified (time/date-time 2015 4 28 18 0)
              :last_synced   (time/date-time 2015 4 30 18 0)
              :redirect_url  "http://localhost/foo"})

(def foo-kio-app {:id      "foo"
                  :team_id "bar"
                  :name    "The Foo App"})

(deftest sync-app
  ; make sure, sync-app executes sync-user, sync-password and sync-client
  (let [calls (atom #{})]
    (with-redefs [j/sync-user (track calls :sync-user [0 1 2])
                  j/sync-password (track calls :sync-password [0 1 2])
                  j/sync-client (track calls :sync-client [0])
                  storage/get-app (lookup {"foo" foo-app})
                  apps/get-app (lookup {"foo" foo-kio-app})]
      (j/sync-app test-config "foo")
      (is (= #{[:sync-user [foo-app foo-kio-app test-config]]
               [:sync-password [foo-app foo-kio-app test-config]]
               [:sync-client [foo-app]]} @calls)))))

(deftest sync-delete-inactive-apps
  ; check for inactive apps
  (let [calls (atom #{})]

    (with-redefs [services/list-users (always #{"stups_foo"})
                  services/delete-user (track calls :delete-service [1])]

      (j/sync-user foo-app (assoc foo-kio-app :active false) test-config)
      (is (= #{[:delete-service ["stups_foo"]]} @calls)))))

(deftest sync-delete-inactive-apps-ignore
  ; check for inactive apps
  (let [calls (atom #{})]

    (with-redefs [services/list-users (always #{})
                  services/delete-user (track calls :delete-service)]

      (j/sync-user foo-app (assoc foo-kio-app :active false) test-config)
      (is (= #{} @calls)))))

(deftest sync-skip-unmodified-user
  ; check for inactive apps
  (let [calls (atom #{})]
    (with-redefs [services/list-users (always #{"stups_foo"})
                  services/delete-user (track calls :delete-service)
                  services/create-or-update-user (track calls :create-or-update-user [])]
      (j/sync-user foo-app (assoc foo-kio-app :active true) test-config)
      (is (= #{} @calls)))))

;; password and client secret rotation

(deftest password-rotation
  nil)
