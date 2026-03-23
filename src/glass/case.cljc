(ns glass.case
  (:require
   [glass.keyword :as keyword]
   [camel-snake-kebab.core :as csk]
   [camel-snake-kebab.extras :refer [transform-keys]]))

(defn camel
  ;; TODO: Maybe use protocols? This is very bad, remove or refactor
  [x target]
  (let [transformer (case target
                      :keyword csk/->camelCaseKeyword
                      :string csk/->camelCaseString
                      :symbol csk/->camelCaseSymbol
                      csk/->camelCaseString)]
    (if (map? x) (transform-keys transformer x) (transformer x))))

(defn kebab
  ;; TODO: Maybe use protocols?
  [x target]
  (let [transformer (case target
                      :keyword csk/->kebab-case-keyword
                      :string csk/->kebab-case-string
                      :symbol csk/->kebab-case-symbol
                      csk/->kebab-case-string)]
    (if (coll? x)
      (transform-keys (comp transformer keyword/transform) x)
      (transformer x))))

(defn snake
  ;; TODO: Maybe use protocols?
  [x target]
  (let [transformer (case target
                      :keyword csk/->snake_case_keyword
                      :string csk/->snake_case_string
                      :symbol csk/->snake_case_symbol
                      csk/->snake_case_string)]
    (if (map? x) (transform-keys transformer x) (transformer x))))
