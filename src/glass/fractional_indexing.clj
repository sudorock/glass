(ns glass.fractional-indexing
  (:require
   [clojure.string :as str])
  (:import
   [clojure.lang ExceptionInfo]))

(def ^:private base-62-digits
  "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz")

(defn- invalid-order-key!
  [key]
  (throw (ex-info (str "invalid order key: " key) {:key key})))

(defn- invalid-integer!
  [int-part]
  (throw (ex-info (str "invalid integer part of order key: " int-part) {:int int-part})))

(defn- valid-digit?
  [digits ch]
  (some? (str/index-of digits ch)))

(defn- midpoint
  "Find lexicographic midpoint between fractional parts a and b.
  a may be empty, b is nil or non-empty. No trailing zeros allowed."
  [a b digits]
  (let [zero (first digits)]
    (when (and b (>= (compare a b) 0))
      (throw (ex-info (str a " >= " b) {:a a :b b})))
    (when (or (and (seq a) (= (last a) zero))
              (and b (seq b) (= (last b) zero)))
      (throw (ex-info "trailing zero" {:a a :b b})))

    (if b
      (let [n (count (take-while (fn [i]
                                   (= (get a i zero) (get b i)))
                                 (range)))]
        (if (pos? n)
          (str (subs b 0 n) (midpoint (subs a n) (subs b n) digits))
          (let [digit-a (if (seq a) (str/index-of digits (first a)) 0)
                digit-b (str/index-of digits (first b))
                diff (- digit-b digit-a)]
            (if (> diff 1)
              (str (nth digits (Math/round (double (* 0.5 (+ digit-a digit-b))))))
              (if (> (count b) 1)
                (subs b 0 1)
                (str (nth digits digit-a)
                     (midpoint (if (seq a) (subs a 1) "") nil digits)))))))
      (let [digit-a (if (seq a) (str/index-of digits (first a)) 0)
            digit-b (count digits)
            diff (- digit-b digit-a)]
        (if (> diff 1)
          (str (nth digits (Math/round (double (* 0.5 (+ digit-a digit-b))))))
          (str (nth digits digit-a)
               (midpoint (if (seq a) (subs a 1) "") nil digits)))))))

(defn- integer-length
  "Get expected length of variable-length integer from head character."
  [head]
  (let [c (int head)]
    (cond
      (<= (int \a) c (int \z)) (+ (- c (int \a)) 2)
      (<= (int \A) c (int \Z)) (+ (- (int \Z) c) 2)
      :else (throw (ex-info (str "invalid order key head: " head) {:head head})))))

(defn- validate-integer!
  "Validate variable-length integer format."
  [int-part digits]
  (when (not= (count int-part) (integer-length (first int-part)))
    (invalid-integer! int-part))
  (when (not-every? #(valid-digit? digits %) (rest int-part))
    (invalid-integer! int-part)))

(defn- integer-part
  "Extract integer part from order key."
  [key digits]
  (when (empty? key)
    (invalid-order-key! key))
  (let [len (integer-length (first key))]
    (when (> len (count key))
      (invalid-order-key! key))
    (let [int-part (subs key 0 len)]
      (validate-integer! int-part digits)
      int-part)))

(defn- validate-order-key!
  "Validate complete order key format."
  [key digits]
  (let [zero (first digits)]
    (when (= key (str "A" (apply str (repeat 26 zero))))
      (invalid-order-key! key))
    (let [i (integer-part key digits)
          f (subs key (count i))]
      (when (not-every? #(valid-digit? digits %) f)
        (invalid-order-key! key))
      (when (and (seq f) (= (last f) zero))
        (invalid-order-key! key)))))

(defn valid-fractional-index?
  "Return true when key is a valid fractional index."
  ([key] (valid-fractional-index? key base-62-digits))
  ([key digits]
   (try
     (validate-order-key! key digits)
     true
     (catch ExceptionInfo _e
       false))))

(defn- increment-integer
  "Increment variable-length integer. Returns nil if max reached."
  [x digits]
  (validate-integer! x digits)
  (let [[head & digs-seq] x
        digs (vec digs-seq)
        zero (first digits)
        max-digit (dec (count digits))]
    (loop [i (dec (count digs))
           carry true
           result digs]
      (if (and carry (>= i 0))
        (let [d (inc (str/index-of digits (nth result i)))]
          (if (= d (count digits))
            (recur (dec i) true (assoc result i zero))
            (recur (dec i) false (assoc result i (nth digits d)))))
        (if carry
          (cond
            (= head \Z) (str "a" zero)
            (= head \z) nil
            :else (let [h (char (inc (int head)))]
                    (if (> (compare h \a) 0)
                      (str h (apply str result) zero)
                      (str h (apply str (drop-last result))))))
          (str head (apply str result)))))))

(defn- decrement-integer
  "Decrement variable-length integer. Returns nil if min reached."
  [x digits]
  (validate-integer! x digits)
  (let [[head & digs-seq] x
        digs (vec digs-seq)
        zero (first digits)
        max-digit (dec (count digits))]
    (loop [i (dec (count digs))
           borrow true
           result digs]
      (if (and borrow (>= i 0))
        (let [d (dec (str/index-of digits (nth result i)))]
          (if (= d -1)
            (recur (dec i) true (assoc result i (nth digits max-digit)))
            (recur (dec i) false (assoc result i (nth digits d)))))
        (if borrow
          (cond
            (= head \a) (str "Z" (nth digits max-digit))
            (= head \A) nil
            :else (let [h (char (dec (int head)))]
                    (if (< (compare h \Z) 0)
                      (str h (apply str result) (nth digits max-digit))
                      (str h (apply str (drop-last result))))))
          (str head (apply str result)))))))

(defn generate-key-between
  "Generate order key between a and b (both can be nil).
  a < b lexicographically when both non-nil.
  digits is string of characters in ascending order (default base-62)."
  ([a b] (generate-key-between a b base-62-digits))
  ([a b digits]
   (when a (validate-order-key! a digits))
   (when b (validate-order-key! b digits))
   (when (and a b (>= (compare a b) 0))
     (throw (ex-info (str a " >= " b) {:a a :b b})))

   (let [zero (first digits)]
     (cond
       (nil? a)
       (if (nil? b)
         (str "a" zero)
         (let [ib (integer-part b digits)
               fb (subs b (count ib))
               smallest-int (str "A" (apply str (repeat 26 zero)))]
           (cond
             (= ib smallest-int) (str ib (midpoint "" fb digits))
             (< (compare ib b) 0) ib
             :else (or (decrement-integer ib digits)
                       (throw (ex-info "cannot decrement any more" {:ib ib}))))))

       (nil? b)
       (let [ia (integer-part a digits)
             fa (subs a (count ia))
             i (increment-integer ia digits)]
         (if i i (str ia (midpoint fa nil digits))))

       :else
       (let [ia (integer-part a digits)
             fa (subs a (count ia))
             ib (integer-part b digits)
             fb (subs b (count ib))]
         (if (= ia ib)
           (str ia (midpoint fa fb digits))
           (let [i (increment-integer ia digits)]
             (when (nil? i)
               (throw (ex-info "cannot increment any more" {:ia ia})))
             (if (< (compare i b) 0)
               i
               (str ia (midpoint fa nil digits))))))))))

(defn generate-n-keys-between
  "Generate n keys between a and b (both can be nil).
  Returns evenly distributed keys for better space efficiency."
  ([a b n] (generate-n-keys-between a b n base-62-digits))
  ([a b n digits]
   (cond
     (zero? n) []
     (= n 1) [(generate-key-between a b digits)]
     (nil? b) (loop [c (generate-key-between a b digits)
                     result [c]
                     i 1]
                (if (>= i n)
                  result
                  (let [next-c (generate-key-between c b digits)]
                    (recur next-c (conj result next-c) (inc i)))))
     (nil? a) (loop [c (generate-key-between a b digits)
                     result [c]
                     i 1]
                (if (>= i n)
                  (vec (reverse result))
                  (let [next-c (generate-key-between a c digits)]
                    (recur next-c (conj result next-c) (inc i)))))
     :else (let [mid (quot n 2)
                 c (generate-key-between a b digits)]
             (into []
                   (concat (generate-n-keys-between a c mid digits)
                           [c]
                           (generate-n-keys-between c b (- n mid 1) digits)))))))
