(ns glass.python
  (:require
   [clojure.java.io :as io]
   [clojure.java.shell :as sh]
   [clojure.string :as str]
   [libpython-clj2.python :as py]))

(defonce ^:private runtime* (atom nil))

(defn- normalize-python-executable
  [python-executable]
  (-> python-executable
      io/file
      .getAbsolutePath))

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

(defn- build-runtime
  [python-executable]
  (let [python-executable (normalize-python-executable python-executable)
        site-packages (site-packages-path python-executable)]
    (py/initialize! :python-executable python-executable)
    (py/call-attr (py/get-attr (py/import-module "sys") "path")
                  "append"
                  site-packages)
    {:python-executable python-executable
     :modules (atom {})}))

(defn init
  [python-executable]
  (locking runtime*
    (if-let [runtime @runtime*]
      (if (= (:python-executable runtime)
             (normalize-python-executable python-executable))
        runtime
        (throw (ex-info "Python runtime already initialized with different executable"
                        {:python-executable (:python-executable runtime)
                         :requested-python-executable python-executable})))
      (let [runtime (build-runtime python-executable)]
        (reset! runtime* runtime)
        runtime))))

(defn runtime
  []
  (or @runtime*
      (throw (ex-info "Python runtime not initialized"
                      {:expected-call "(glass.python/init \"/path/to/python\")"}))))
