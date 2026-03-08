(ns glass.mcp.system.http
  (:require
   [glass.mcp.system.router :as router]
   [reitit.ring :as reitit-ring]
   [ring.adapter.jetty :as jetty])
  (:import
   [org.eclipse.jetty.server Server]))

(defn start!
  [{:keys [port]
    :or {port 3000}}]
  (jetty/run-jetty
   (reitit-ring/ring-handler
    (router/router)
    (reitit-ring/create-default-handler))
   {:port port
    :output-buffer-size 1
    :join? false}))

(defn stop!
  [^Server jetty]
  (.stop jetty))
