(ns org.zalando.stups.mint.worker.test-helpers)

(defn track
  "Returns a function that conjs its arguments into the atom"
  ([a action]
   (fn [& args]
     (let [prev (or (get @a action)
                    [])]
     (swap! a assoc action (conj prev args))))))

(defn throwing
  "Returns a function that throws with the provided arguments when executed"
  [& [msg data]]
  (fn [& args]
    (throw (ex-info (or msg "any exception")
                    (or data {})))))

(defmacro third
  "Just as first, second"
  [coll]
  `(nth ~coll 2))