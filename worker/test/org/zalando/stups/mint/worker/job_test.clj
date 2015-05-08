(ns org.zalando.stups.mint.worker.job-test
  (:require [clojure.test :refer :all]
            [io.sarnowski.swagger1st.parser]                ; Joda DateTime extension for json/JSONWriter. Needed for logging.
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

(def test-tokens
  {})

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
    (j/run-sync test-config test-tokens)))

(deftest resiliency-list-unavailable
  ; error on getting list -> no bubble up, so job restarts in a imnute
  (with-redefs [storage/list-apps (always (throw (ex-info "this should be catched" {})))]
    (j/run-sync test-config test-tokens)))

(deftest resiliency-individual-app
  ; error on processing on app-> no bubble up, so job restarts in a minute
  (with-redefs [storage/list-apps (always [{:id "foo"}])
                storage/get-app (always (throw (ex-info "this should be catched" {})))]
    (j/run-sync test-config test-tokens)))


;; synchronisation of apps

(deftest sync-add-all-accs
  ; make sure, sync-app will be called for every account
  (let [calls (atom #{})]

    (with-redefs [storage/list-apps (always [{:id "foo"}, {:id "bar"}])
                  j/sync-app (track calls :sync-app)]

      (j/run-sync test-config test-tokens)
      (is (= #{[:sync-app "foo"] [:sync-app "bar"]} @calls)))))

(def foo-app {:id            "foo"
              :username      "stups_foo"
              :last_modified (time/date-time 2015 4 30 18 0)
              :last_synced   (time/date-time 2015 4 28 18 0)
              :redirect_url  "http://localhost/foo"})

(def foo-kio-app {:id      "foo"
                  :team_id "bar"
                  :name    "The Foo App"
                  :active  true})

(deftest sync-app
  ; make sure, sync-app executes sync-user, sync-password and sync-client
  (let [calls (atom #{})]
    (with-redefs [j/sync-user (track calls :sync-user [0 1 2])
                  j/sync-password (track calls :sync-password [0 1 2])
                  j/sync-client (track calls :sync-client [0])
                  storage/get-app (lookup {"foo" foo-app})
                  apps/get-app (lookup {"foo" foo-kio-app})]
      (j/sync-app test-config "foo" test-tokens)
      (is (= #{[:sync-user [foo-app foo-kio-app test-config]]
               [:sync-password [foo-app foo-kio-app test-config]]
               [:sync-client [foo-app]]} @calls)))))

(deftest sync-delete-inactive-apps
  ; makes sure, that an app which became inactive, but has already been synced before, will be deleted
  (let [calls (atom #{})]

    (with-redefs [services/list-users (always #{"stups_foo"})
                  services/delete-user (track calls :delete-service [1])]

      (j/sync-user foo-app (assoc foo-kio-app :active false) test-config test-tokens)
      (is (= #{[:delete-service ["stups_foo"]]} @calls)))))

(deftest sync-delete-inactive-apps-ignore
  ; an inactive app, which has not been synced yet, should be ignored
  (let [calls (atom #{})]

    (with-redefs [services/list-users (always #{})
                  services/delete-user (track calls :delete-service)]

      (j/sync-user foo-app (assoc foo-kio-app :active false) test-config test-tokens)
      (is (= #{} @calls)))))

(deftest sync-skip-unmodified-user
  ; an app, that has already been synced and did not change again, should simply be skipped
  (let [calls (atom #{})]
    (with-redefs [services/list-users (always #{"stups_foo"})
                  services/delete-user (track calls :delete-service)
                  services/create-or-update-user (track calls :create-or-update-user [])]
      (j/sync-user (assoc foo-app :last_modified (time/date-time 2015 04 24 12 00)) foo-kio-app test-config test-tokens)
      (is (= #{} @calls)))))

(deftest create-user
  ; a completely new app should be synced
  (let [calls (atom #{})]
    (with-redefs [services/create-or-update-user (track calls :create-or-update-user [1 2])
                  storage/update-status (track calls :update-status [1])]
      (j/sync-user (assoc foo-app :last_synced nil) foo-kio-app test-config test-tokens)
      (is (= #{[:create-or-update-user ["stups_foo"
                                        {:client_config {:redirect_urls ["http://localhost/foo"]
                                                         :scopes        []}
                                         :id            "stups_foo"
                                         :name          "The Foo App"
                                         :owner         "bar"
                                         :user_config   {:scopes []}}]]
               [:update-status ["foo"]]} @calls)))))

(deftest update-user
  ; a completely new app should be synced
  (let [calls (atom #{})]
    (with-redefs [services/create-or-update-user (track calls :create-or-update-user [1 2])
                  storage/update-status (track calls :update-status [1 2])]
      (j/sync-user foo-app foo-kio-app test-config test-tokens)
      (is (= (count @calls) 2))
      (is (some #(= % [:create-or-update-user ["stups_foo"
                                               {:client_config {:redirect_urls ["http://localhost/foo"]
                                                                :scopes        []}
                                                :id            "stups_foo"
                                                :name          "The Foo App"
                                                :owner         "bar"
                                                :user_config   {:scopes []}}]]) @calls))
      (is (some #(let [call-id (nth % 0)] (= call-id :update-status)) @calls)))))


;; password and client secret rotation

(deftest password-rotation
  nil)
