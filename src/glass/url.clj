(ns glass.url
  (:import
   (java.net URLEncoder URLDecoder)))

(defn encode
  [s]
  (URLEncoder/encode ^String s "UTF-8"))

(defn decode
  [s]
  (URLDecoder/decode ^String s "UTF-8"))
