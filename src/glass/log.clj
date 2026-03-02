(ns glass.log
  (:require
   [glass.exception :as ex]
   [glass.json :as json]
   [clj-log4j2.core :as log])
  (:import
   [clojure.lang IPersistentMap]))

(extend-protocol log/LogObject
  IPersistentMap
  (log-object [this] (json/stringify this)))

(defmacro info [m] `(log/info ~m))

(defmacro warn [m] `(log/warn ~m))

(defmacro error [m] `(log/error ~m))

(defmacro debug [m] `(log/debug ~m))

(defmacro exception
  [ex m]
  `(log/error (merge {:exception (ex/exception->map ~ex)} ~m)))
