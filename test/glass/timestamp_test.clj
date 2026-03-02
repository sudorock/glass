(ns glass.timestamp-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [glass.timestamp :as ts])
  (:import
   [java.time Instant LocalDateTime ZoneId]
   [java.util Locale]))

(deftest ->ts-number-test
  (testing "numeric values are converted to epoch milliseconds"
    (is (= 42 (ts/->ts 42)))
    (is (= 42 (ts/->ts (int 42))))))

(deftest ->ts-string-test
  (testing "offset datetime strings are parsed with format"
    (let [s "2024-03-05T14:30:00+00:00"
          fmt "yyyy-MM-dd'T'HH:mm:ssXXX"]
      (is (= 1709649000000 (ts/->ts s fmt)))))

  (testing "locale arity parses localized patterns"
    (let [s "05-Mar-2024 14:30"
          fmt "dd-MMM-yyyy HH:mm"
          expected (-> (LocalDateTime/of 2024 3 5 14 30)
                       (.atZone (ZoneId/of "UTC"))
                       .toInstant
                       .toEpochMilli)]
      (is (= expected (ts/->ts s fmt Locale/ENGLISH))))))

(deftest ->str-test
  (testing "formats epoch milliseconds with optional timezone"
    (let [epoch 1709649000000]
      (is (= "2024-03-05 14:30" (ts/->str epoch "yyyy-MM-dd HH:mm")))
      (is (= "2024-03-05 20:00"
             (ts/->str epoch "yyyy-MM-dd HH:mm" "Asia/Kolkata"))))))

(deftest add-subtract-test
  (testing "adds and subtracts chrono units"
    (let [base (.toEpochMilli (Instant/parse "2024-03-05T14:30:00Z"))]
      (is (= (+ base (* 2 60 60 1000))
             (ts/add base :hours 2)))
      (is (= base
             (-> base
                 (ts/add :days 3)
                 (ts/subtract :days 3)))))))
