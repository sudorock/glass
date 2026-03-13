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

(defn- encode-options
  [{:keys [allowed-special disallowed-special]}]
  (cond-> {}
    (some? allowed-special)
    (assoc :allowed_special
           (if (coll? allowed-special)
             (py/->python (set allowed-special))
             allowed-special))

    (some? disallowed-special)
    (assoc :disallowed_special
           (if (coll? disallowed-special)
             (py/->python (set disallowed-special))
             disallowed-special))))

(defn- token-count
  [runtime text {:keys [encoding]
                 :or {encoding default-encoding}
                 :as opts}]
  (let [encoded (py/call-attr-kw
                 (py/py. (import-module runtime "tiktoken") get_encoding encoding)
                 "encode"
                 [text]
                 (encode-options opts))]
    (count encoded)))

(defn count-text
  ([runtime text]
   (count-text runtime text {}))
  ([runtime text opts]
   (token-count runtime text opts)))

(defn count-file
  ([runtime path]
   (count-file runtime path {}))
  ([runtime path opts]
   (token-count runtime (slurp (io/file path) :encoding "UTF-8") opts)))
