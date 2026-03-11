(ns glass.mcp.system.http
  (:require
   [glass.mcp.system.router :as router]
   [reitit.ring :as reitit-ring]
   [ring.adapter.jetty :as jetty])
  (:import
   [org.eclipse.jetty.server Server]))

(defn start!
  [{:keys [port max-idle-time]
    :or {port 3000
         max-idle-time 0}}]
  (jetty/run-jetty
   (reitit-ring/ring-handler
    (router/router)
    (reitit-ring/create-default-handler))
   {:port port
    :max-idle-time max-idle-time
    :output-buffer-size 1
    :join? false}))

(defn stop!
  [^Server jetty]
  (.stop jetty))
