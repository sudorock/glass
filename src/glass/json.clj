(ns glass.json
  (:require
   [cheshire.core :as json]))

(defn- parse-key-fn
  [{:keys [keywordize? key-fn] :or {keywordize? true}}]
  (if keywordize?
    (or key-fn true)
    false))

(defn stringify
  [x]
  (json/generate-string x))

(defn parse
  ([s] (json/parse-string s true))
  ([s {:keys [keywordize? key-fn] :or {keywordize? true}}]
   (json/parse-string s (parse-key-fn {:keywordize? keywordize?
                                       :key-fn key-fn}))))

(defn parse-stream
  ([reader]
   (json/parsed-seq reader true))
  ([reader opts]
   (json/parsed-seq reader (parse-key-fn opts))))
