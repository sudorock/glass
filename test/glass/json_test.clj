(ns glass.json-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [glass.json :as json]))

(deftest stringify-test
  (testing "serializes maps to JSON strings"
    (is (= "{\"a\":1}" (json/stringify {:a 1})))) )

(deftest parse-test
  (testing "parses JSON with keywordized keys by default"
    (is (= {:a 1} (json/parse "{\"a\":1}"))))
  (testing "parses JSON without keywordizing keys"
    (is (= {"a" 1} (json/parse "{\"a\":1}" {:keywordize? false}))))
  (testing "parses JSON with custom key function"
    (is (= {"A" 1}
           (json/parse "{\"a\":1}" {:key-fn str/upper-case})))) )
