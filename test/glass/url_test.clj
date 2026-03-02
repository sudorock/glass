(ns glass.url-test
  (:require
   [clojure.test :refer [deftest is]]
   [glass.url :as url]))

(deftest encode-decode-roundtrip-test
  (let [s "a b+c/d?e=f&g=h"]
    (is (= s (url/decode (url/encode s))))))
