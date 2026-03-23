(ns glass.case-test
  #?(:clj
     (:require
      [clojure.test :refer [deftest is testing]]
      [glass.case :as case])
     :cljs
     (:require
      [cljs.test :refer-macros [deftest is testing]]
      [glass.case :as case])))

(deftest camel-test
  (testing "transforms scalar values"
    (is (= "myValue" (case/camel "my-value" :string)))
    (is (= :myValue (case/camel :my-value :keyword))))
  (testing "transforms map keys"
    (is (= {:firstName "Ada" :lastName "Lovelace"}
           (case/camel {:first-name "Ada" :last-name "Lovelace"} :keyword)))))

(deftest kebab-test
  (testing "transforms scalar values"
    (is (= "my-value" (case/kebab "myValue" :string)))
    (is (= :my-value (case/kebab :myValue :keyword))))
  (testing "transforms map keys"
    (is (= {:first-name "Ada" :last-name "Lovelace"}
           (case/kebab {:firstName "Ada" :lastName "Lovelace"} :keyword)))))

(deftest snake-test
  (testing "transforms scalar values"
    (is (= "my_value" (case/snake "myValue" :string)))
    (is (= :my_value (case/snake :myValue :keyword))))
  (testing "transforms map keys"
    (is (= {:first_name "Ada" :last_name "Lovelace"}
           (case/snake {:firstName "Ada" :lastName "Lovelace"} :keyword)))))
