(ns glass.mcp.http-api
  (:require
   [glass.json :as json]
   [glass.mcp.json-rpc :as json-rpc]
   [glass.mcp.protocol :as mcp]
   [glass.mcp.state :as state])
  (:import
   [java.util UUID]
   [java.util.concurrent BlockingQueue]))

(defn- start-sse-stream
  [session-id conn-id]
  (fn [emit close]
    (let [emit* (fn [response]
                  (emit
                   (merge {:event "message"}
                          (update response :data json/stringify))))
          close* (fn []
                   (swap! state/state update-in [:sessions session-id :connections] dissoc conn-id)
                   (close))]
      (swap! state/state assoc-in [:sessions session-id :connections conn-id] {:emit emit*
                                                                                :close close*}))))

(defn POST
  {:parameters
   {:body [:map {:closed false}
           [:jsonrpc [:enum "2.0"]]
           [:method {:optional true} string?]
           [:id {:optional true} any?]
           [:response {:optional true} [:map {:closed false}]]
           [:params {:optional true} [:or
                                      [:map {:closed false}]
                                      [:vector any?]]]]}}
  [{:keys [parameters mcp-session-id] :as req}]
  (let [{:keys [method params result error id] :as rpc-req} (:body parameters)]
    (cond
      (and (not mcp-session-id) (not= "initialize" method))
      {:status 400
       :body {:result {:error "Missing Mcp-Session-Id header"}}}

      (and mcp-session-id (= "initialize" method))
      {:status 400
       :body {:result {:error "Re-initializing existing session"}}}

      (and mcp-session-id (not (get-in @state/state [:sessions mcp-session-id])))
      {:status 404
       :body {:result {:error (str "No session with Mcp-Session-Id "
                                   mcp-session-id " found")}}}

      (not id)
      (do
        (mcp/handle-notification (assoc rpc-req :state state/state :session-id mcp-session-id))
        {:status 202})

      (and (or result error) mcp-session-id id)
      (do
        (when-let [request (get-in @state/state [:requests id])]
          (let [conn-id (str (UUID/randomUUID))
                handle-response (fn []
                                  (swap! state/state update :requests dissoc id)
                                  ((or (:callback request) mcp/handle-response)
                                   (assoc request
                                          :state state/state
                                          :session-id mcp-session-id
                                          :connection-id conn-id
                                          :result result
                                          :error error)))]
            (if (:sse req)
              {:status 200
               :sse/handler
               (fn [emit close]
                 ((start-sse-stream mcp-session-id conn-id) emit close)
                 (handle-response))}
              (do
                (handle-response)
                {:status 202}))))
        {:status 202})

      (and method id)
      (let [session-id (or mcp-session-id (str (UUID/randomUUID)))
            conn-id (str (UUID/randomUUID))]
        (if (:sse req)
          {:status 200
           :mcp-session-id session-id
           :sse/handler
           (fn [emit close]
             ((start-sse-stream session-id conn-id) emit close)
             (mcp/handle-request (assoc rpc-req
                                        :state state/state
                                        :session-id session-id
                                        :connection-id conn-id)))}
          (do
            (mcp/handle-request (assoc rpc-req :state state/state :session-id session-id))
            {:status 200
             :mcp-session-id session-id}))))))

(defn GET
  [{:keys [mcp-session-id sse]}]
  (if sse
    {:status 200
     :sse/handler
     (fn [emit close]
       (future
         (let [queue (get-in @state/state [:sessions mcp-session-id :connections :default :queue])]
           (try
             (when queue
               (loop [response (.take ^BlockingQueue queue)]
                 (emit
                  (merge {:event "message"}
                         (update response :data json/stringify)))
                 (recur (.take ^BlockingQueue queue))))
             (finally
               (close))))))}
    {:status 400
     :body {:error {:code json-rpc/invalid-request
                    :message "GET request must accept text/event-stream"}}}))

(defn routes
  []
  [["/mcp" {:get #'GET :post #'POST}]])
