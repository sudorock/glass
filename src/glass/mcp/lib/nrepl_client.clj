(ns glass.mcp.lib.nrepl-client
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [nrepl.transport :as transport])
  (:import
   [java.net Socket]))

(defn nrepl-port
  [loc]
  (let [path (cond
               (str/starts-with? loc "file:///")
               (subs loc 7)
               (str/starts-with? loc "file://")
               (subs loc 6)
               :else
               loc)
        file (io/file path ".nrepl-port")]
    (when (.exists file)
      (parse-long (slurp file)))))

(defn connect
  [{:keys [host port]}]
  (let [socket (Socket. host port)
        in (io/input-stream (.getInputStream socket))
        out (io/output-stream (.getOutputStream socket))
        state (atom {})
        trans (transport/bencode in out socket)]
    (.start
     (Thread/ofVirtual)
     (fn []
       (while (not (.isClosed socket))
         (when-let [msg (transport/recv trans)]
           (when-let [callback (get-in @state [:callbacks (:id msg)])]
             (when (some #{"done" "error"} (:status msg))
               (swap! state update :callbacks dissoc (:id msg)))
             (try
               (callback msg)
               (catch Throwable _)))))))
    {:state state
     :socket socket
     :transport trans}))

(defonce msg-id-cnt (atom 0))

(defn send-msg
  ([conn msg]
   (let [result (promise)
         responses (volatile! [])]
     (send-msg conn msg (fn [response]
                          (vswap! responses conj response)
                          (when (some #{"done" "error"} (:status response))
                            (deliver result @responses))))
     result))
  ([conn msg callback]
   (let [id (swap! msg-id-cnt inc)
         session-id (:session-id conn)
         msg (cond-> (assoc msg :id id)
               session-id (assoc :session session-id))]
     (swap! (:state conn) assoc-in [:callbacks id] callback)
     (transport/send (:transport conn) msg)
     conn)))

(defn new-session
  [conn]
  (let [session-id (promise)]
    (send-msg conn {:op "clone"} (fn [{:keys [new-session]}]
                                   (deliver session-id new-session)))
    (assoc conn :session-id @session-id)))
