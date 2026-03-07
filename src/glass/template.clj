(ns glass.template
  (:require
   [selmer.parser :as selmer]))

(defn render
  [path params]
  (selmer/render-file path params))

