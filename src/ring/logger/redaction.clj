(ns ring.logger.redaction
  (:require [clojure.walk :as walk]))

(defn redact-map [m {:keys [redact-key? redact-value-fn]}]
  "Redacts the values found in m for each key in redact-keys.
  The redacted value is obtained by applying redact-value-fn to key and value"
  (walk/postwalk (fn [x]
                   (if (map? x)
                     (->> x
                          (map (fn [[k v]]
                                 (if (redact-key? (keyword k))
                                   [k (redact-value-fn k v)]
                                   [k v])))
                          (into {}))
                     x))
                 m))
