(ns org.zalando.stups.mint.worker.job.common
  (:require [clj-time.format :as f]
            [org.zalando.stups.friboo.config :as config]
            [org.zalando.stups.mint.worker.external.scopes :as scopes]))

(def not-nil?
  (comp not nil?))

(defmacro busy-map
  "The opposite of a lazy map -> (doall (map args))"
  [f coll]
  `(doall (map ~f ~coll)))

(defmacro has-error
  "Returns first non-nil value or nil"
  [expr]
  `(some identity ~expr))

(defn parse-date-time
  [string]
  (when string (f/parse (f/formatters :date-time) string)))

(defn format-date-time
  [date-time]
  (f/unparse (f/formatters :date-time) date-time))

(defn owners [{:keys [resource_type_id]} essentials-url tokens]
  (:resource_owners (scopes/get-resource-type essentials-url resource_type_id tokens)))

(defn owner-scope? [{:keys [resource_type_id scope_id]} essentials-url tokens]
  (:is_resource_owner_scope (scopes/get-scope essentials-url resource_type_id scope_id tokens)))

(defn group-types [scopes configuration tokens]
  (let [essentials-url (config/require-config configuration :essentials-url)]
    (->> scopes
         (map #(assoc % :id (str (:resource_type_id %) "." (:scope_id %))))
         (map #(assoc % :scope-type (if (owner-scope? % essentials-url tokens) :owner-scope :application-scope)))
         (map (fn [scope]
                (if (= :owner-scope (:scope-type scope))
                  (assoc scope :owners (owners scope essentials-url tokens))
                  scope)))
         (group-by :scope-type))))

(defn add-scope [result scope-id [owner & owners]]
  (if owner
    (let [result (update-in result [owner] conj scope-id)]
      (recur result scope-id owners))
    result))

(defn map-owner-scopes [scopes]
  (->> scopes
       (reduce
         (fn [result scope]
           (add-scope result (:id scope) (:owners scope)))
         {})
       (map (fn [[owner scopes]]
              {:realm owner
               :sopes scopes}))))

(defn map-application-scopes [scopes]
  (map :id scopes))

(defn map-scopes [scopes configuration tokens]
  (-> scopes
      (group-types configuration tokens)
      (update-in [:owner-scope] map-owner-scopes)
      (update-in [:application-scope] map-application-scopes)))