(ns glass.uuid
  (:import
   (java.util UUID)))

(set! *warn-on-reflection* true)

(defn random-uuid-str
  []
  (str (UUID/randomUUID)))
