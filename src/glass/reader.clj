(ns glass.reader
  (:require
   [aero.core :as aero]
   [integrant.core :as ig]))

(def reader aero/reader)
(def read-config aero/read-config)

(defmethod reader 'ig/ref
  [_opts _tag value]
  (ig/ref value))

(defmethod reader 'ig/refset
  [_opts _tag value]
  (ig/refset value))

(defmethod reader 'env
  [_opts _tag key]
  (System/getenv key))
