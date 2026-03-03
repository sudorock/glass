(ns glass.mcp.system.router
  (:require
   [glass.mcp.http-api :as api]
   [glass.mcp.lib.ring-sse :as ring-sse]
   [muuntaja.core :as muuntaja]
   [reitit.coercion.malli]
   [reitit.ring :as ring]
   [reitit.ring.coercion :as ring-coercion]
   [reitit.ring.middleware.muuntaja :as reitit-muuntaja]
   [reitit.ring.middleware.parameters :as reitit-params]))

(def malli-coercion-options
  {:error-keys #{:type :coercion :in :schema :value :errors :humanized :transformed}})

(defn muuntaja-instance
  []
  (muuntaja/create muuntaja/default-options))

(defn- update-handler
  [{:keys [handler] :as verb-data}]
  (if (var? handler)
    (merge verb-data (meta handler))
    verb-data))

(defn- compile-var-meta
  [route-data]
  (reduce (fn [d m]
            (if (contains? d m)
              (update d m update-handler)
              d))
          route-data
          [:get :post :put :delete]))

(defn wrap-mcp-headers
  [handler]
  (fn [req]
    (let [id (get-in req [:headers "mcp-session-id"])
          version (get-in req [:headers "mcp-protocol-version"])
          res (handler (cond-> req
                         id (assoc :mcp-session-id id)
                         version (assoc :mcp-protocol-version version)))
          id (or id (get res :mcp-session-id))]
      (if id
        (assoc-in res [:headers "Mcp-Session-Id"] id)
        res))))

(defn reitit-compile-fn
  [[path data] opts]
  (ring/compile-result [path (compile-var-meta data)] opts))

(defn router
  []
  (let [routes (into ["" {}
                      ["/ping" {:get (constantly {:status 200 :body "pong"})}]]
                     (api/routes))]
    (ring/router
     routes
     {:compile reitit-compile-fn
      :data
      {:coercion (reitit.coercion.malli/create malli-coercion-options)
       :muuntaja (muuntaja-instance)
       :middleware [reitit-params/parameters-middleware
                    reitit-muuntaja/format-negotiate-middleware
                    reitit-muuntaja/format-response-middleware
                    reitit-muuntaja/format-request-middleware
                    ring-coercion/coerce-exceptions-middleware
                    ring-coercion/coerce-response-middleware
                    ring-coercion/coerce-request-middleware
                    wrap-mcp-headers
                    ring-sse/wrap-sse]}})))
