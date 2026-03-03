(ns glass.mcp.system.stdio
  (:require
   [clojure.java.io :as io]
   [glass.json :as json]
   [glass.mcp.protocol :as mcp]
   [glass.mcp.state :as state]))

(defn- emit-json
  [data]
  (println (json/stringify data))
  (flush))

(defn start!
  []
  (swap! state/state assoc-in [:sessions :stdio :connections :default]
         {:emit #(emit-json (:data %))
          :close (fn [])})
  (future
    (with-open [reader (io/reader *in*)]
      (doseq [rpc-req (json/parse-stream reader)]
        (if (:id rpc-req)
          (mcp/handle-request (assoc rpc-req :state state/state :session-id :stdio :connection-id :default))
          (mcp/handle-notification (assoc rpc-req :state state/state :session-id :stdio)))))))
