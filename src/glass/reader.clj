(ns glass.reader
  (:require
   [aero.core :as aero]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [integrant.core :as ig]))

(def reader aero/reader)

(defn- parse-dotenv-line
  [line-number line]
  (let [line (str/trim line)]
    (cond
      (str/blank? line) nil
      (str/starts-with? line "#") nil
      :else
      (let [separator-index (str/index-of line "=")
            key (when separator-index
                  (subs line 0 separator-index))]
        (when-not (and separator-index (not (str/blank? key)))
          (throw (ex-info "Invalid .env line"
                          {:line-number line-number
                           :line line})))
        [key (subs line (inc separator-index))]))))

(defn- load-dotenv
  []
  (when-let [resource (io/resource ".env")]
    (with-open [reader (io/reader resource)]
      (reduce
       (fn [dotenv [line-number line]]
         (if-let [[key value] (parse-dotenv-line line-number line)]
           (assoc dotenv key value)
           dotenv))
       {}
       (map-indexed (fn [index line] [(inc index) line])
                    (line-seq reader))))))

(defn read-config
  ([source]
   (read-config source {}))
  ([source opts]
   (aero/read-config source (assoc opts :dotenv (load-dotenv)))))

(defmethod reader 'sys/ref
  [_opts _tag value]
  (ig/ref value))

(defmethod reader 'sys/var
  [_opts _tag value]
  (ig/var value))

(defmethod reader 'sys/refset
  [_opts _tag value]
  (ig/refset value))

(defmethod reader 'env
  [opts _tag key]
  (let [key (str key)]
    (or (System/getenv key)
        (get (:dotenv opts) key))))
