(ns glass.system
  (:require
   [clojure.java.io :as io]
   [glass.reader :as reader]
   [integrant.core :as ig]))

(def init-key ig/init-key)

(defmethod init-key :default [_ x] x)

(defn init
  [conf-path profile]
  (-> (io/resource conf-path)
      (reader/read-config {:profile profile})
      ig/init))
