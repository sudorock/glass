(ns glass.keyword
  (:require
   [clojure.walk :as walk]))

(defn unqualify
  [x]
  (walk/postwalk
   (fn [x]
     (if (qualified-keyword? x)
       (keyword (name x))
       x))
   x))

(defn ->str
  [kw]
  (try
    (if-let [ns (namespace kw)]
      (str ns "/" (name kw))
      (name kw))
    (catch Exception _e nil)))

(defn transform
  [x]
  (walk/postwalk (fn [x] (if (keyword? x) (->str x) x)) x))
