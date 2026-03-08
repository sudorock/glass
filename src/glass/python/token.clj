(ns glass.python.token
  (:require
   [clojure.java.io :as io]
   [libpython-clj2.python :as py]))

(def ^:private default-encoding "cl100k_base")

(defn- import-module
  [runtime module-name]
  (or (get @(:modules runtime) module-name)
      (let [module (py/import-module module-name)]
        (swap! (:modules runtime) assoc module-name module)
        module)))

(defn- token-count
  [runtime text encoding]
  (let [encoded (py/py. (py/py. (import-module runtime "tiktoken") get_encoding encoding)
                        encode
                        text)]
    (count encoded)))

(defn count-text
  ([runtime text]
   (count-text runtime text {:encoding default-encoding}))
  ([runtime text {:keys [encoding]
                  :or {encoding default-encoding}}]
   (token-count runtime text encoding)))

(defn count-file
  ([runtime path]
   (count-file runtime path {:encoding default-encoding}))
  ([runtime path {:keys [encoding]
                  :or {encoding default-encoding}}]
   (token-count runtime (slurp (io/file path) :encoding "UTF-8") encoding)))
