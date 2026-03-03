(ns glass.mcp
  "MCP SDK entry points."
  (:require
   [glass.mcp.json-rpc :as json-rpc]
   [glass.mcp.protocol :as protocol]
   [glass.mcp.state :as state]
   [glass.mcp.system.http :as http]
   [glass.mcp.system.stdio :as stdio]
   [glass.mcp.system.watch-state :as watch]))

(def state
  state/state)

(def protocol-version
  state/protocol-version)

(defn initial-state
  []
  (state/initial-state))

(defn add-tool
  [tool]
  (state/add-tool tool))

(defn add-resource
  [resource]
  (state/add-resource resource))

(defn add-prompt
  [prompt]
  (state/add-prompt prompt))

(defn set-instructions
  [instructions]
  (state/set-instructions instructions))

(defn request
  [req]
  (protocol/request req))

(defn notify
  [req]
  (protocol/notify req))

(def json-rpc-parse-error json-rpc/parse-error)
(def json-rpc-invalid-request json-rpc/invalid-request)
(def json-rpc-method-not-found json-rpc/method-not-found)
(def json-rpc-invalid-params json-rpc/invalid-params)
(def json-rpc-internal-error json-rpc/internal-error)

(defn json-rpc-request
  [id method params]
  (json-rpc/request id method params))

(defn json-rpc-notification
  ([method]
   (json-rpc/notification method))
  ([method params]
   (json-rpc/notification method params)))

(defn json-rpc-response
  [id result]
  (json-rpc/response id result))

(defn json-rpc-error
  [id details]
  (json-rpc/error id details))

(defn run-http!
  ([] (run-http! {}))
  ([opts]
   (http/start! opts)
   (watch/start! opts)))

(defn run-stdio!
  ([] (run-stdio! {}))
  ([opts]
   (stdio/start!)
   (watch/start! opts)))
