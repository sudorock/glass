(ns glass.system
  (:refer-clojure :exclude [ref])
  (:require
   [integrant.core :as ig]))

(def init ig/init)
(def bind ig/bind)
(def halt! ig/halt!)
(def resume ig/resume)
(def suspend! ig/suspend!)

(def init-key ig/init-key)
(def halt-key! ig/halt-key!)
(def resume-key ig/resume-key)
(def suspend-key! ig/suspend-key!)

(def load-namespaces ig/load-namespaces)
(def ref ig/ref)
(def var ig/var)
(def refset ig/refset)

(defmethod init-key :default [_ x] x)
