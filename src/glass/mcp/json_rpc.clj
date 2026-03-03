(ns glass.mcp.json-rpc)

(def parse-error -32700)
(def invalid-request -32600)
(def method-not-found -32601)
(def invalid-params -32602)
(def internal-error -32603)

(defn request
  [id method params]
  (cond-> {:jsonrpc "2.0"
           :id id
           :method method}
    (seq params)
    (assoc :params params)))

(defn notification
  ([method]
   {:jsonrpc "2.0"
    :method method})
  ([method params]
   (cond-> {:jsonrpc "2.0"
            :method method}
     (seq params)
     (assoc :params params))))

(defn response
  [id result]
  {:jsonrpc "2.0"
   :id id
   :result result})

(defn error
  [id {:keys [message code data]}]
  {:jsonrpc "2.0"
   :id id
   :error (cond-> {:code code}
            message (assoc :message message)
            data (assoc :data data))})
