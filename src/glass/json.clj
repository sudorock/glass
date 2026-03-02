(ns glass.json
  (:require
   [cheshire.core :as json]))

(defn stringify
  [x]
  (json/generate-string x))

(defn parse
  ([s] (json/parse-string s true))
  ([s {:keys [keywordize? key-fn] :or {keywordize? true}}]
   (if keywordize?
     (json/parse-string s (or key-fn true))
     (json/parse-string s))))
