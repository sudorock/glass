(ns glass.fractional-indexing-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [glass.fractional-indexing :refer [generate-key-between
                                      generate-n-keys-between]]))

(deftest generate-key-between-test
  (testing "basic operations"
    (is (= "a0" (generate-key-between nil nil)))
    (is (= "Zz" (generate-key-between nil "a0")))
    (is (= "Zy" (generate-key-between nil "Zz")))
    (is (= "a1" (generate-key-between "a0" nil)))
    (is (= "a2" (generate-key-between "a1" nil)))
    (is (= "a0V" (generate-key-between "a0" "a1")))
    (is (= "a1V" (generate-key-between "a1" "a2")))
    (is (= "a0l" (generate-key-between "a0V" "a1")))
    (is (= "ZzV" (generate-key-between "Zz" "a0")))
    (is (= "a0" (generate-key-between "Zz" "a1")))
    (is (= "Xzzz" (generate-key-between nil "Y00")))
    (is (= "c000" (generate-key-between "bzz" nil)))
    (is (= "a0G" (generate-key-between "a0" "a0V")))
    (is (= "a08" (generate-key-between "a0" "a0G")))
    (is (= "b127" (generate-key-between "b125" "b129")))
    (is (= "a1" (generate-key-between "a0" "a1V")))
    (is (= "a0" (generate-key-between "Zz" "a01")))
    (is (= "a0" (generate-key-between nil "a0V")))
    (is (= "b99" (generate-key-between nil "b999"))))

  (testing "boundary conditions"
    (is (thrown-with-msg? Exception #"invalid order key: A00000000000000000000000000"
                          (generate-key-between nil "A00000000000000000000000000")))
    (is (= "A000000000000000000000000000V"
           (generate-key-between nil "A000000000000000000000000001")))
    (is (= "zzzzzzzzzzzzzzzzzzzzzzzzzzz"
           (generate-key-between "zzzzzzzzzzzzzzzzzzzzzzzzzzy" nil)))
    (is (= "zzzzzzzzzzzzzzzzzzzzzzzzzzzV"
           (generate-key-between "zzzzzzzzzzzzzzzzzzzzzzzzzzz" nil))))

  (testing "error cases"
    (is (thrown-with-msg? Exception #"invalid order key: a00"
                          (generate-key-between "a00" nil)))
    (is (thrown-with-msg? Exception #"invalid order key: a00"
                          (generate-key-between "a00" "a1")))
    (is (thrown-with-msg? Exception #"invalid order key head: 0"
                          (generate-key-between "0" "1")))
    (is (thrown-with-msg? Exception #"a1 >= a0"
                          (generate-key-between "a1" "a0")))))

(deftest generate-n-keys-between-test
  (let [base-10-digits "0123456789"]
    (testing "batch generation"
      (is (= ["a0" "a1" "a2" "a3" "a4"]
             (generate-n-keys-between nil nil 5 base-10-digits)))
      (is (= ["a5" "a6" "a7" "a8" "a9" "b00" "b01" "b02" "b03" "b04"]
             (generate-n-keys-between "a4" nil 10 base-10-digits)))
      (is (= ["Z5" "Z6" "Z7" "Z8" "Z9"]
             (generate-n-keys-between nil "a0" 5 base-10-digits)))
      (is (= ["a01" "a02" "a03" "a035" "a04" "a05" "a06" "a07" "a08" "a09"
              "a1" "a11" "a12" "a13" "a14" "a15" "a16" "a17" "a18" "a19"]
             (generate-n-keys-between "a0" "a2" 20 base-10-digits))))))

(deftest base-95-test
  (let [base-95-digits " !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~"]
    (testing "base-95 encoding"
      (is (= "a00P" (generate-key-between "a00" "a01" base-95-digits)))
      (is (= "a0/P" (generate-key-between "a0/" "a00" base-95-digits)))
      (is (= "a " (generate-key-between nil nil base-95-digits)))
      (is (= "a!" (generate-key-between "a " nil base-95-digits)))
      (is (= "Z~" (generate-key-between nil "a " base-95-digits)))
      (is (thrown-with-msg? Exception #"invalid order key: a0 "
                            (generate-key-between "a0 " "a0!" base-95-digits)))
      (is (= "A                          ("
             (generate-key-between nil "A                          0" base-95-digits)))
      (is (= "b  " (generate-key-between "a~" nil base-95-digits)))
      (is (= "a " (generate-key-between "Z~" nil base-95-digits)))
      (is (thrown-with-msg? Exception #"invalid order key: b   "
                            (generate-key-between "b   " nil base-95-digits)))
      (is (= "a0;" (generate-key-between "a0" "a0V" base-95-digits)))
      (is (= "a  1P" (generate-key-between "a  1" "a  2" base-95-digits)))
      (is (thrown-with-msg? Exception #"invalid order key: A                          "
                            (generate-key-between nil "A                          " base-95-digits))))))
