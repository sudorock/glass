(ns glass.system
  (:refer-clojure :exclude [ref])
  (:require
   [clojure.java.io :as io]
   [glass.reader :as reader]
   [integrant.core :as ig]))

(def init ig/init)
(def halt! ig/halt!)
(def resume ig/resume)
(def suspend! ig/suspend!)

(def init-key ig/init-key)
(def halt-key! ig/halt-key!)
(def resume-key ig/resume-key)
(def suspend-key! ig/suspend-key!)

(def load-namespaces ig/load-namespaces)
(def ref ig/ref)
(def refset ig/refset)

(defmethod init-key :default [_ x] x)

(defn init-from-resource
  [conf-path profile]
  (-> (io/resource conf-path)
      (reader/read-config {:profile profile})
      init))
