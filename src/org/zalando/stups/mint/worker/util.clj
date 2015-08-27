(ns org.zalando.stups.mint.worker.util
  (:require [clojure.string :as s]))

(defmacro xor
  [a b]
  `(or (and ~a (not ~b))
       (and (not ~a) ~b)))

(defn conpath
  "Concatenates path elements to an URL."
  [url & path]
  (let [[x & xs] path]
    (if x
      (let [url (if (xor (.endsWith url "/")
                         (.startsWith x "/"))
                  ; concat if exactly one of them has a /
                  (str url x)
                  (if (and (.endsWith url "/")
                           (.startsWith x "/"))
                    ; if both have a /, remove one
                    (str url (s/replace-first x #"/" ""))
                    ; if none have a /, add one
                    (str url "/" x)))]
        (recur url xs))
      url)))
