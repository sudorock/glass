(ns glass.keyword-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [glass.keyword :as keyword]))

(deftest unqualify-test
  (testing "removes namespaces from all qualified keywords"
    (is (= {:id 1
            :nested {:name "Ada"}
            :items [:x :y]}
           (keyword/unqualify {:user/id 1
                               :nested {:person/name "Ada"}
                               :items [:a/x :b/y]})))))

(deftest keyword->str-test
  (testing "returns keyword text for qualified and unqualified keywords"
    (is (= "user/id" (keyword/->str :user/id)))
    (is (= "id" (keyword/->str :id))))
  (testing "returns nil for non-keyword values"
    (is (nil? (keyword/->str nil)))))

(deftest transform-test
  (testing "converts all keywords in nested data to strings"
    (is (= {"user/id" 42
            "tags" ["a" "b"]
            "nested" {"profile/name" "Ada"}}
           (keyword/transform {:user/id 42
                               :tags [:a :b]
                               :nested {:profile/name "Ada"}})))))
