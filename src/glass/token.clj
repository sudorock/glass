(ns glass.token
  (:require
   [clojure.java.shell :as sh]
   [clojure.string :as str]
   [clojure.java.io :as io]
   [glass.reader :as reader]
   [libpython-clj2.python :as py]))

(def ^:private default-encoding "cl100k_base")

(defn- python-config
  []
  (-> (io/resource "python.edn")
      (reader/read-config {})))

(defn- site-packages-path
  [python-executable]
  (let [{:keys [exit out err]}
        (sh/sh python-executable
               "-c"
               "import site; print(site.getsitepackages()[0])")]
    (if (zero? exit)
      (str/trim out)
      (throw (ex-info "Python site-packages lookup failed"
                      {:exit exit
                       :err err})))))

(def ^:private runtime
  (delay
    (let [{:keys [python-executable] :as config} (python-config)]
      (when-not python-executable
        (throw (ex-info "Missing Python executable for glass.token"
                        {:expected-env "GLASS_PYTHON_EXECUTABLE"})))
      (apply py/initialize! (mapcat identity config))
      (py/call-attr (py/get-attr (py/import-module "sys") "path")
                    "append"
                    (site-packages-path python-executable)))
    {:tiktoken (py/import-module "tiktoken")}))

(defn- tiktoken-module
  []
  (:tiktoken @runtime))

(defn- token-count
  [text encoding]
  (let [encoded (py/py. (py/py. (tiktoken-module) get_encoding encoding) encode text)]
    (count encoded)))

(defn count-text
  ([text]
   (count-text text {:encoding default-encoding}))
  ([text {:keys [encoding]
          :or {encoding default-encoding}}]
   (token-count text encoding)))

(defn count-file
  ([path]
   (count-file path {:encoding default-encoding}))
  ([path {:keys [encoding]
          :or {encoding default-encoding}}]
   (token-count (slurp (io/file path) :encoding "UTF-8") encoding)))
