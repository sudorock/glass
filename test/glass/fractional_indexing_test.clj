(ns glass.fractional-indexing-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [glass.fractional-indexing :refer [generate-key-between
                                      generate-n-keys-between
                                      valid-fractional-index?]]))

(deftest valid-fractional-index?-test
  (let [base-62-digits "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
        base-95-digits " !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~"]
    (testing "valid keys without fractional part"
      (is (true? (valid-fractional-index? "a0")))
      (is (true? (valid-fractional-index? "a1")))
      (is (true? (valid-fractional-index? "Zz")))
      (is (true? (valid-fractional-index? "Y00")))
      (is (true? (valid-fractional-index? "b00")))
      (is (true? (valid-fractional-index? "A000000000000000000000000001"))))

    (testing "valid keys with fractional parts"
      (is (true? (valid-fractional-index? "a0V")))
      (is (true? (valid-fractional-index? "a01")))
      (is (true? (valid-fractional-index? "a0lV")))
      (is (true? (valid-fractional-index? "a0001")))
      (is (true? (valid-fractional-index? "zzzzzzzzzzzzzzzzzzzzzzzzzzzV"))))

    (testing "valid keys with explicit base-62 digits"
      (is (true? (valid-fractional-index? "a0V" base-62-digits))))

    (testing "valid keys with base-95 digits"
      (is (true? (valid-fractional-index? "a " base-95-digits)))
      (is (true? (valid-fractional-index? "a0;" base-95-digits))))

    (testing "nil input"
      (is (false? (valid-fractional-index? nil))))

    (testing "empty string"
      (is (false? (valid-fractional-index? ""))))

    (testing "invalid fractional character"
      (is (false? (valid-fractional-index? "a0?"))))

    (testing "invalid head character"
      (is (false? (valid-fractional-index? "000")))
      (is (false? (valid-fractional-index? "1"))))

    (testing "too-short integer part"
      (is (false? (valid-fractional-index? "b")))
      (is (false? (valid-fractional-index? "b1"))))

    (testing "trailing zero in fractional part"
      (is (false? (valid-fractional-index? "a00")))
      (is (false? (valid-fractional-index? "a0V0"))))

    (testing "smallest key rejection"
      (is (false? (valid-fractional-index? "A00000000000000000000000000")))
      (is (false? (valid-fractional-index? "A                          " base-95-digits))))

    (testing "invalid digit in integer part"
      (is (false? (valid-fractional-index? "a?")))
      (is (false? (valid-fractional-index? "b0?0V"))))

    (testing "generated keys are valid"
      (let [k1 (generate-key-between nil nil)
            k2 (generate-key-between k1 nil)
            k3 (generate-key-between nil k1)
            k4 (generate-key-between k1 k2)
            k5 (generate-key-between "zzzzzzzzzzzzzzzzzzzzzzzzzzz" nil)]
        (is (true? (valid-fractional-index? k1)))
        (is (true? (valid-fractional-index? k2)))
        (is (true? (valid-fractional-index? k3)))
        (is (true? (valid-fractional-index? k4)))
        (is (true? (valid-fractional-index? k5)))))))

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
    (is (thrown-with-msg? Exception #"invalid order key: a0\?"
                          (generate-key-between "a0?" nil)))
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
