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
    (is (= "myValue" (case/camel :string "my-value")))
    (is (= :myValue (case/camel :keyword :my-value))))
  (testing "transforms map keys"
    (is (= {:firstName "Ada" :lastName "Lovelace"}
           (case/camel :keyword {:first-name "Ada" :last-name "Lovelace"})))))

(deftest kebab-test
  (testing "transforms scalar values"
    (is (= "my-value" (case/kebab :string "myValue")))
    (is (= :my-value (case/kebab :keyword :myValue))))
  (testing "transforms map keys"
    (is (= {:first-name "Ada" :last-name "Lovelace"}
           (case/kebab :keyword {:firstName "Ada" :lastName "Lovelace"})))))

(deftest snake-test
  (testing "transforms scalar values"
    (is (= "my_value" (case/snake :string "myValue")))
    (is (= :my_value (case/snake :keyword :myValue))))
  (testing "transforms map keys"
    (is (= {:first_name "Ada" :last_name "Lovelace"}
           (case/snake :keyword {:firstName "Ada" :lastName "Lovelace"})))))
