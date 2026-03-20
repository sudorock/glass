(ns glass.uuid
  (:import
   (java.security SecureRandom)
   (java.util UUID)))

(set! *warn-on-reflection* true)

(def ^:private ^SecureRandom secure-random
  (SecureRandom.))

(def ^:private rand-a-mask
  0x0fff)

(def ^:private rand-b-mask
  0x3fffffffffffffff)

(defn uuid4
  []
  (str (UUID/randomUUID)))

(defn uuid7
  []
  (let [timestamp (System/currentTimeMillis)
        rand-a (bit-and (.nextInt ^SecureRandom secure-random) rand-a-mask)
        rand-b (bit-and (.nextLong ^SecureRandom secure-random) rand-b-mask)
        most-significant-bits (bit-or (bit-shift-left timestamp 16)
                                      (bit-shift-left 0x7 12)
                                      rand-a)
        least-significant-bits (bit-or (bit-shift-left 0x2 62)
                                       rand-b)]
    (str (UUID. most-significant-bits least-significant-bits))))
