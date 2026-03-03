(ns glass.mcp.lib.ring-sse
  (:require
   [clojure.string :as str]
   [ring.util.io :as ring-io]
   [ring.util.response :as response])
  (:import
   [java.io OutputStream OutputStreamWriter Writer]))

(set! *warn-on-reflection* true)

(defn write-sse-message
  [^Writer writer {:keys [event data id comment]}]
  (when comment
    (doseq [line (str/split comment #"\R")]
      (.write writer (str ": " line "\n"))))
  (when event
    (.write writer (str "event: " event "\n")))
  (when id
    (.write writer (str "id: " id "\n")))
  (when data
    (doseq [line (str/split data #"\R")]
      (.write writer (str "data: " line "\n"))))
  (.write writer "\n"))

(defn accepts-sse?
  [{:keys [headers]}]
  (str/includes? (or (get headers "accept") "") "text/event-stream"))

(defn upgrade-response
  [res]
  (let [res (cond-> res
              (not (response/find-header res "content-type"))
              (assoc-in [:headers "content-type"] "text/event-stream"))]
    (assoc res
           :body
           (ring-io/piped-input-stream
            (fn [^OutputStream out]
              (let [wait (promise)
                    writer (OutputStreamWriter. out "UTF-8")
                    emit (fn [message]
                           (write-sse-message writer message)
                           (.flush writer))
                    close (fn []
                            (deliver wait :ok)
                            (.close writer))]
                ((:sse/handler res) emit close)
                @wait))))))

(defn wrap-sse
  [handler]
  (fn [req]
    (let [sse? (accepts-sse? req)
          res (handler (cond-> req sse? (assoc :sse sse?)))]
      (if (and sse? (contains? res :sse/handler))
        (upgrade-response res)
        res))))
