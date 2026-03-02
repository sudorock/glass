(ns glass.http
  (:refer-clojure :exclude [get])
  (:require
   [hato.client :as client]))

(def ^:private client
  (delay (client/build-http-client {:connect-timeout 10000
                                    :redirect-policy :always})))

(defn- default-opts
  []
  {:throw-exceptions? false :http-client @client})

(defn get
  [url opts]
  (client/get url (merge (default-opts) opts)))

(defn post
  [url opts]
  (client/post url (merge (default-opts) opts)))

(defn patch
  [url opts]
  (client/patch url (merge (default-opts) opts)))

(defn delete
  [url opts]
  (client/delete url (merge (default-opts) opts)))

(defn purge
  [url opts]
  (client/request
   (merge (default-opts) opts {:request-method :purge :url url})))
