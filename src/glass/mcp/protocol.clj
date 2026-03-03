(ns glass.mcp.protocol
  (:require
   [glass.mcp.json-rpc :as json-rpc])
  (:import
   [java.util.concurrent LinkedBlockingQueue]))

(defmulti handle-request (fn [request] (:method request)))
(defmulti handle-response (fn [request] (:method request)))
(defmulti handle-notification (fn [request] (:method request)))

(defn default-conn
  [state session-id]
  (get-in state [:sessions session-id :connections :default]))

(defn find-conn
  [state session-id connection-id]
  (or (when (and connection-id (not= :default connection-id))
        (get-in state [:sessions session-id :connections connection-id]))
      (default-conn state session-id)))

(defn empty-response
  [request]
  (let [{:keys [state session-id connection-id]} request
        state @state]
    (when-let [{:keys [close]} (find-conn state session-id connection-id)]
      (close))))

(defonce req-id-counter (atom 0))

(defn request
  [{:keys [state session-id connection-id method params callback] :as req}]
  (let [id (swap! req-id-counter inc)]
    (when-let [{:keys [emit]} (find-conn @state session-id connection-id)]
      (swap! state assoc-in [:requests id] (dissoc req :state))
      (emit {:data (json-rpc/request id method params)}))))

(defn notify
  [{:keys [state session-id method params]}]
  (when-let [{:keys [emit]} (default-conn @state session-id)]
    (emit {:data (if params
                   (json-rpc/notification method params)
                   (json-rpc/notification method))})))

(defn reply
  [request response]
  (let [{:keys [state session-id connection-id]} request]
    (when-let [{:keys [emit close]} (find-conn @state session-id connection-id)]
      (emit {:data response})
      (close))))

(defn swap-sess!
  [req f & args]
  (when-let [sess (:session-id req)]
    (apply swap! (:state req) update-in [:sessions sess] f args)))

(defmethod handle-request "initialize" [{:keys [id state params] :as req}]
  (let [{:keys [protocolVersion capabilities clientInfo procolversion]} params
        protocol-version (or protocolVersion procolversion)
        queue (LinkedBlockingQueue. 1024)]
    (swap-sess! req
                (fn [sess]
                  (-> sess
                      (update :connections
                              assoc :default
                              {:emit #(.put queue %)
                               :close (fn [])
                               :queue queue})
                      (assoc :protocolVersion protocol-version
                             :capabilities capabilities
                             :clientInfo clientInfo))))
    (let [{:keys [protocol-version capabilities server-info instructions]} @state]
      (reply
       req
       (json-rpc/response
        id
        {:protocolVersion protocol-version
         :capabilities capabilities
         :serverInfo server-info
         :instructions instructions})))))

(defmethod handle-request "logging/setLevel" [{:keys [id params] :as req}]
  (reply req
         (json-rpc/response
          id
          {}))
  (swap-sess! req assoc :logging params))

(defmethod handle-notification "notifications/initialized" [{:keys [state session-id]}]
  (let [capabilities (get-in @state [:sessions session-id :capabilities])]
    (when (contains? capabilities :roots)
      (request {:state state
                :session-id session-id
                :method "roots/list"}))))

(defmethod handle-notification "notifications/cancelled" [_req]
  nil)

(defmethod handle-request "tools/list" [{:keys [id state] :as req}]
  (reply req
         (json-rpc/response
          id
          {:tools
           (into []
                 (map #(assoc (dissoc (val %) :tool-fn) :name (key %)))
                 (get @state :tools))})))

(defmethod handle-request "tools/call" [{:keys [id state params] :as req}]
  (let [{:keys [name arguments]} params
        {:keys [tool-fn]} (get-in @state [:tools name])]
    (if tool-fn
      (reply req (json-rpc/response id (tool-fn req arguments)))
      (reply req (json-rpc/error id {:code json-rpc/invalid-params :message "Tool not found"})))))

(defmethod handle-request "prompts/list" [{:keys [id state] :as req}]
  (reply
   req
   (json-rpc/response
    id
    {:prompts
     (into []
           (map #(assoc (dissoc (val %) :messages-fn) :name (key %)))
           (get @state :prompts))})))

(defmethod handle-request "prompts/get" [{:keys [id state params] :as req}]
  (let [{:keys [name arguments]} params
        {:keys [description messages-fn]} (get-in @state [:prompts name])]
    (if messages-fn
      (reply req (json-rpc/response id {:description description
                                        :messages (messages-fn arguments)}))
      (reply req (json-rpc/error id {:code json-rpc/invalid-params :message "Prompt not found"})))))

(defmethod handle-request "resources/list" [{:keys [id state] :as req}]
  (let [resources (into []
                        (map #(assoc (dissoc (val %) :load-fn) :uri (key %)))
                        (get @state :resources))]
    (reply req
           (json-rpc/response
            id
            {:resources resources}))))

(defmethod handle-request "resources/templates/list" [{:keys [id] :as req}]
  (reply req (json-rpc/response id {:resourceTemplates []})))

(defmethod handle-request "resources/read" [{:keys [id state params] :as req}]
  (let [uri (:uri params)]
    (if-let [res (get-in @state [:resources uri])]
      (reply req
             (json-rpc/response
              id
              {:contents [(assoc res :uri uri :text ((:load-fn res)))]}))
      (reply req (json-rpc/error id {:code json-rpc/invalid-params :message "Resource not found"})))))

(defmethod handle-response "roots/list" [{:keys [state session-id result] :as req}]
  (swap! state assoc-in [:sessions session-id :roots] (:roots result))
  (empty-response req))
