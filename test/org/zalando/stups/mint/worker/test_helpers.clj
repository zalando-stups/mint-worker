(ns org.zalando.stups.mint.worker.test-helpers)

(def test-config
  {:storage-url      "https://localhost"
   :kio-url          "https://localhost"
   :service-user-url "https://localhost"
   :mint-storage-url "https://localhost"
   :essentials-url   "https://localhost"
   :prefix           "stups_"
   :max-s3-errors    10})

(def test-tokens
  {})

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

(defn sequentially
  "Returns a function that returns provided arguments sequentially on every call"
  [& args]
  (let [calls (atom -1)
        limit (dec (count args))]
    (fn [& inner]
      (if (neg? limit)
          nil
          (do
            (swap! calls inc)
            (nth args (if (> @calls limit)
                        limit
                        @calls)))))))

(defmacro one?
  "Just as zero?, but with one. Disclaimer: Only ints and bigints, no floats."
  [n]
  `(= 1 ~n))