(ns glass.system
  (:require
   [clojure.java.io :as io]
   [glass.reader :as reader]
   [integrant.core :as ig]))

(def init-key ig/init-key)
(def halt-key! ig/halt-key!)

(defmethod init-key :default [_ x] x)

(defn init
  [conf-path profile]
  (-> (io/resource conf-path)
      (reader/read-config {:profile profile})
      ig/init))

(defn halt!
  ([system]
   (ig/halt! system))
  ([system keys]
   (ig/halt! system keys)))
